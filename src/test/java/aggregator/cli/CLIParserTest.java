package aggregator.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CLIParserTest {
    @Test
    void parsesCollectCommand() {
        CLIParser.Options options = new CLIParser(new String[]{
                "collect", "--apis", "weather,github", "--format", "csv",
                "--mode", "append", "--file", "data/out.csv",
                "--param", "weather.latitude=48.8"
        }).parse();

        assertEquals("collect", options.command());
        assertEquals("csv", options.format());
        assertTrue(options.append());
        assertEquals(2, options.apis().size());
        assertEquals("48.8", options.parameters().get("weather").get("latitude"));
    }

    @Test
    void infersReadFormatFromFileExtension() {
        CLIParser.Options options = new CLIParser(new String[]{
                "read", "--file", "data/out.json", "--source", "weather"
        }).parse();

        assertEquals("json", options.format());
        assertEquals("weather", options.source());
    }
}
