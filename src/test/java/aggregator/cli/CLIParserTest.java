package aggregator.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CLIParserTest {
    @Test
    void parsesCollectCommandAndRemovesDuplicateApiNames() {
        CLIParser.Options options = new CLIParser(new String[]{
                "collect", "--apis", "weather,users,comments,weather", "--format", "csv",
                "--mode", "append", "--file", "src/main/resources/out.csv",
                "--param", "weather.latitude=48.8"
        }).parse();

        assertEquals("collect", options.command());
        assertEquals("csv", options.format());
        assertTrue(options.append());
        assertEquals(3, options.apis().size());
        assertEquals("48.8", options.parameters().get("weather").get("latitude"));
    }

    @Test
    void recognizesHelpWithNoArgumentsOrHelpFlags() {
        assertEquals("help", new CLIParser(new String[0]).parse().command());
        assertEquals("help", new CLIParser(new String[]{"--help"}).parse().command());
        assertEquals("help", new CLIParser(new String[]{"collect", "-h"}).parse().command());
    }

    @Test
    void infersReadFormatFromFileExtension() {
        CLIParser.Options options = new CLIParser(new String[]{
                "read", "--file", "src/main/resources/out.json", "--source", "weather"
        }).parse();

        assertEquals("json", options.format());
        assertEquals("weather", options.source());
    }

    @Test
    void parsesReadCsvAndDefaultsToCreateMode() {
        CLIParser.Options options = new CLIParser(new String[]{
                "read", "--file", "records.CSV", "--mode", "create"
        }).parse();

        assertEquals("csv", options.format());
        assertEquals(false, options.append());
    }

    @Test
    void rejectsUnknownCommandsAndOptions() {
        assertThrows(IllegalArgumentException.class,
                () -> new CLIParser(new String[]{"delete", "--file", "x.json"}).parse());
        assertThrows(IllegalArgumentException.class,
                () -> new CLIParser(new String[]{"read", "--file", "x.json", "--unknown", "value"}).parse());
        assertThrows(IllegalArgumentException.class,
                () -> new CLIParser(new String[]{"read", "value"}).parse());
    }

    @Test
    void rejectsMissingOptionValuesAndRepeatedSingleValueOptions() {
        assertThrows(IllegalArgumentException.class,
                () -> new CLIParser(new String[]{"read", "--file"}).parse());
        assertThrows(IllegalArgumentException.class,
                () -> new CLIParser(new String[]{"read", "--file", "a.json", "--file", "b.json"}).parse());
    }

    @Test
    void rejectsInvalidParameterSyntaxAndInvalidMode() {
        assertThrows(IllegalArgumentException.class,
                () -> new CLIParser(new String[]{"collect", "--file", "x.json", "--param", "city=Berlin"}).parse());
        assertThrows(IllegalArgumentException.class,
                () -> new CLIParser(new String[]{"read", "--file", "x.json", "--mode", "merge"}).parse());
    }

    @Test
    void validatesRequiredCollectArgumentsAndFormat() {
        assertThrows(IllegalArgumentException.class,
                () -> new CLIParser(new String[]{"collect", "--file", "x.json", "--format", "json"}).parse());
        assertThrows(IllegalArgumentException.class,
                () -> new CLIParser(new String[]{"collect", "--file", "x.json", "--apis", "weather"}).parse());
        assertThrows(IllegalArgumentException.class,
                () -> new CLIParser(new String[]{"collect", "--file", "x.txt", "--apis", "weather", "--format", "xml"}).parse());
    }

    @Test
    void rejectsFileFormatMismatchesAndReadOptionsOnCollect() {
        assertThrows(IllegalArgumentException.class,
                () -> new CLIParser(new String[]{"collect", "--file", "x.csv", "--apis", "weather", "--format", "json"}).parse());
        assertThrows(IllegalArgumentException.class,
                () -> new CLIParser(new String[]{"collect", "--file", "x.json", "--apis", "weather", "--format", "json", "--source", "weather"}).parse());
    }

    @Test
    void requiresExplicitReadFormatForUnknownFileExtension() {
        assertThrows(IllegalArgumentException.class,
                () -> new CLIParser(new String[]{"read", "--file", "records.dat"}).parse());
    }
}
