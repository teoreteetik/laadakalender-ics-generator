package it.lemb.laadakalender;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.Integer.parseInt;
import static java.util.Map.entry;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;

public class Main {

    public record Fair(
            String name,
            Optional<String> url,
            String county,
            LocalDate date) {
    }

    public static final Map<String, Integer> MONTH_BY_NAME = Map.ofEntries(
            entry("jaanuar", 1),
            entry("veebruar", 2),
            entry("märts", 3),
            entry("aprill", 4),
            entry("mai", 5),
            entry("juuni", 6),
            entry("juuli", 7),
            entry("august", 8),
            entry("september", 9),
            entry("oktoober", 10),
            entry("november", 11),
            entry("detsember", 12)
    );

    public static final String LAADAKALENDER_URL = "https://laadakalender.ee";
    public static final String ICS_OUTPUT_FILE_PATH = "laadakalender.ics";

    public static void main(String...args) throws Exception {
        List<Fair> fairs = extractFairs(LAADAKALENDER_URL);
        String ics = generateIcs(fairs);
        Files.writeString(Paths.get(ICS_OUTPUT_FILE_PATH), ics);

        System.out.println("Wrote ICS file to " + ICS_OUTPUT_FILE_PATH);
    }

    // Parses the HTML source of the laadakalender webpage into a list of Fair objects
    // Example of the HTML at resources/laadakalender-html-source-example
    public static List<Fair> extractFairs(String laadakalenderUrl) throws Exception {
        List<Fair> fairs = new ArrayList<>();
        Document doc = Jsoup.connect(laadakalenderUrl).get();
        Elements elements = doc.getAllElements();
        Integer currentMonth = null;
        Integer currentYear = null;
        for (Element element : elements) {
            // Find the H3 headings with the format <name of month> <year>, e.g., "jaanuar 2024" (case insensitive)
            if (element.is("h3")) {
                String heading = element.text().toLowerCase();
                String[] headingParts = heading.split(" ");
                if (headingParts.length == 2 && MONTH_BY_NAME.containsKey(headingParts[0]) && headingParts[1].startsWith("20")) {
                    currentMonth = MONTH_BY_NAME.get(headingParts[0]);
                    currentYear = parseInt(headingParts[1]);
                }
                continue;
            }
            if (currentMonth != null && element.is("table")) {
                // We are now processing the table immediately following the "jaanuar 2024" heading
                Elements fairRows = element.select("tr[class^=\"row-\"]");
                for (Element fairRow : fairRows) {
                    Element link = fairRow.getElementsByTag("a").first();
                    // Parse URL from title. Some fairs do not have a hyperlink.
                    Optional<String> url = ofNullable(link).map(l -> l.attr("href"));

                    // Parse the date from the format "21.01"
                    String[] dayAndMonth = fairRow.getElementsByClass("column-1").first().text().split("\\.");
                    if (dayAndMonth.length < 2) {
                        // Some fairs do not have a date (e.g., some have just "Detsember")
                        // All calendar events should have date, so ignore these
                        continue;
                    }
                    LocalDate date = LocalDate.of(currentYear, parseInt(dayAndMonth[1]), parseInt(dayAndMonth[0]));

                    // Parse name and county from the second and third column
                    String name = fairRow.getElementsByClass("column-2").first().text();
                    String county = fairRow.getElementsByClass("column-3").first().text();
                    Fair fair = new Fair(name, url, county, date);
                    fairs.add(fair);
                }
                currentMonth = null;
                currentYear = null;
            }
        }

        return fairs;
    }

    public static String generateIcs(List<Fair> fairs) {
        // https://en.wikipedia.org/wiki/ICalendar
        DateTimeFormatter icsFormat = DateTimeFormatter.ofPattern("yyyyMMdd");
        String icsEvents = fairs.stream()
                .map(fair -> {
                    int index = fair.county.indexOf("maa");
                    String county = index == -1 ? fair.county : fair.county.substring(0, fair.county.indexOf("maa"));
                    return
                        "BEGIN:VEVENT\n" +
                        "UID:" + fair.date.toString() + "." + fair.name + "\n" +
                        "DTSTART:" + fair.date.format(icsFormat) + "\n" +
                        "DTEND:" + fair.date.plusDays(1).format(icsFormat) + "\n" +
                        "SUMMARY:" + county + ": " + fair.name + "\n" +
                        "LOCATION:" + fair.county + ", Estonia\n" +
                        fair.url.map(url -> "URL:" + url + "\n").orElse("") +
                        "END:VEVENT\n";
                }).collect(joining("\n"));

        return
            "BEGIN:VCALENDAR\n" +
            "VERSION:2.0\n" +
            "PRODID:https://github.com/teoreteetik/laadakalender-ics\n\n" +
            icsEvents + "\n" +
            "END:VCALENDAR";
    }

}
