package aggregator.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CLIParserTest {
    @Test
    void parsesCollectCommand() {
        CLIParser.Options options = new CLIParser(new String[]{
                "collect", "--apis", "weather,users,comments", "--format", "csv",
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
    void recognizesHelpAfterCommandLineOption() {
        CLIParser.Options options = new CLIParser(new String[]{"--help"}).parse();
        assertEquals("help", options.command());
    }

    @Test
    void infersReadFormatFromFileExtension() {
        CLIParser.Options options = new CLIParser(new String[]{
                "read", "--file", "src/main/resources/out.json", "--source", "weather"
        }).parse();

        assertEquals("json", options.format());
        assertEquals("weather", options.source());
    }
}
