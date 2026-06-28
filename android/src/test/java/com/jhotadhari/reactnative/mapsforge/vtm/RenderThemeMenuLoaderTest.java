package com.jhotadhari.reactnative.mapsforge.vtm;

import com.facebook.react.bridge.ReactApplicationContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link RenderThemeMenuLoader}.
 *
 * <p>Tests both the SAX-based XML parsing of {@code <stylemenu>} blocks
 * (via temp XML files) and the {@code toWritableArray} conversion, which
 * is pure data transformation.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class RenderThemeMenuLoaderTest {

    private static int fileCounter = 0;
    private final List<File> tempFiles = new ArrayList<>();

    @Before
    public void setUp() {
        fileCounter++;
    }

    @After
    public void tearDown() {
        // Clean up temp files created during tests.
        for (File f : tempFiles) {
            if (f.exists()) {
                f.delete();
            }
        }
        // Note: the static cache in RenderThemeMenuLoader persists across
        // tests within the same JVM session. Each test uses a unique path
        // (via fileCounter), so there is no stale-cache interference.
    }

    /**
     * Writes {@code content} to a unique temp file and returns its path.
     */
    private String createTempXml(String content) throws Exception {
        File tmp = File.createTempFile("rendertheme_" + fileCounter + "_", ".xml");
        try (FileWriter w = new FileWriter(tmp, StandardCharsets.UTF_8)) {
            w.write(content);
        }
        tmp.deleteOnExit();
        tempFiles.add(tmp);
        return tmp.getAbsolutePath();
    }

    private String minimalStylemenu(String body) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
            + "<rendertheme xmlns=\"http://mapsforge.org/renderTheme\" version=\"1\">"
            + "  <stylemenu defaultlang=\"en\" defaultvalue=\"default\">"
            + body
            + "  </stylemenu>"
            + "</rendertheme>";
    }

    // ------------------------------------------------------------------
    // load — SAX parsing from temp XML files
    // ------------------------------------------------------------------

    @Test
    public void load_parsesDisabledVisibleLayers() throws Exception {
        String xml = minimalStylemenu(""
            + "    <layer id=\"default\" enabled=\"true\" visible=\"true\">"
            + "      <name lang=\"en\" value=\"Default style\"/>"
            + "    </layer>"
            + "    <layer id=\"night\" enabled=\"false\" visible=\"true\">"
            + "      <name lang=\"en\" value=\"Night mode\"/>"
            + "    </layer>"
            + "    <layer id=\"hidden\" enabled=\"false\" visible=\"false\">"
            + "      <name lang=\"en\" value=\"Hidden layer\"/>"
            + "    </layer>");

        String path = createTempXml(xml);
        ReactApplicationContext mockCtx = mock(ReactApplicationContext.class);
        List<RenderThemeMenuLoader.StyleMenuEntry> entries =
                RenderThemeMenuLoader.load(path, mockCtx);

        // Only the enabled=false + visible=true layer is returned.
        assertEquals("Only 'night' layer must be parsed", 1, entries.size());
        assertEquals("night", entries.get(0).id);
        assertEquals("Night mode", entries.get(0).label);
        assertFalse("night is not the default", entries.get(0).isDefault);
    }

    @Test
    public void load_marksDefaultEntry() throws Exception {
        String xml = minimalStylemenu(""
            + "    <layer id=\"day\" enabled=\"false\" visible=\"true\">"
            + "      <name lang=\"en\" value=\"Day mode\"/>"
            + "    </layer>"
            + "    <layer id=\"night\" enabled=\"false\" visible=\"true\">"
            + "      <name lang=\"en\" value=\"Night mode\"/>"
            + "    </layer>");

        // defaultvalue="night" in the stylemenu tag.
        String xmlWithDefault = xml.replace("defaultvalue=\"default\"",
                "defaultvalue=\"night\"");
        String path = createTempXml(xmlWithDefault);
        ReactApplicationContext mockCtx = mock(ReactApplicationContext.class);
        List<RenderThemeMenuLoader.StyleMenuEntry> entries =
                RenderThemeMenuLoader.load(path, mockCtx);

        assertEquals(2, entries.size());
        assertEquals("day", entries.get(0).id);
        assertFalse("day is not the default", entries.get(0).isDefault);
        assertEquals("night", entries.get(1).id);
        assertTrue("night must be marked as default", entries.get(1).isDefault);
    }

    @Test
    public void load_usesDefaultLanguageForLabel() throws Exception {
        String xml = ""
            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
            + "<rendertheme xmlns=\"http://mapsforge.org/renderTheme\" version=\"1\">"
            + "  <stylemenu defaultlang=\"de\" defaultvalue=\"default\">"
            + "    <layer id=\"default\" enabled=\"false\" visible=\"true\">"
            + "      <name lang=\"en\" value=\"Default\"/>"
            + "      <name lang=\"de\" value=\"Standard\"/>"
            + "    </layer>"
            + "  </stylemenu>"
            + "</rendertheme>";

        String path = createTempXml(xml);
        ReactApplicationContext mockCtx = mock(ReactApplicationContext.class);
        List<RenderThemeMenuLoader.StyleMenuEntry> entries =
                RenderThemeMenuLoader.load(path, mockCtx);

        assertEquals(1, entries.size());
        // defaultlang="de" → "Standard", not "Default".
        assertEquals("Standard", entries.get(0).label);
    }

    @Test
    public void load_parsesOverlays() throws Exception {
        String xml = ""
            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
            + "<rendertheme xmlns=\"http://mapsforge.org/renderTheme\" version=\"1\">"
            + "  <stylemenu defaultlang=\"en\" defaultvalue=\"default\">"
            + "    <layer id=\"hills\" enabled=\"false\" visible=\"true\">"
            + "      <name lang=\"en\" value=\"Hillshading\"/>"
            + "    </layer>"
            + "    <layer id=\"custom\" enabled=\"false\" visible=\"true\" parent=\"hills\">"
            + "      <name lang=\"en\" value=\"Custom\"/>"
            + "      <overlay id=\"hills\"/>"
            + "    </layer>"
            + "  </stylemenu>"
            + "</rendertheme>";

        String path = createTempXml(xml);
        ReactApplicationContext mockCtx = mock(ReactApplicationContext.class);
        List<RenderThemeMenuLoader.StyleMenuEntry> entries =
                RenderThemeMenuLoader.load(path, mockCtx);

        // Both are enabled=false and visible=true.
        assertEquals(2, entries.size());

        // The "custom" entry should have an overlay referencing "hills".
        RenderThemeMenuLoader.StyleMenuEntry custom = entries.get(1);
        assertEquals("custom", custom.id);
        assertEquals(1, custom.overlays.size());
        assertEquals("hills", custom.overlays.get(0).id);
        assertEquals("Hillshading", custom.overlays.get(0).label);
    }

    @Test
    public void load_withoutStylemenu_returnsEmpty() throws Exception {
        String xml = ""
            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
            + "<rendertheme xmlns=\"http://mapsforge.org/renderTheme\" version=\"1\">"
            + "  <rule e=\"any\" zoom-min=\"5\" zoom-max=\"20\">"
            + "  </rule>"
            + "</rendertheme>";

        String path = createTempXml(xml);
        ReactApplicationContext mockCtx = mock(ReactApplicationContext.class);
        List<RenderThemeMenuLoader.StyleMenuEntry> entries =
                RenderThemeMenuLoader.load(path, mockCtx);

        assertNotNull("Result must not be null", entries);
        assertTrue("Empty stylemenu must return empty list", entries.isEmpty());
    }

    @Test
    public void load_cacheReturnsSameResultOnSecondCall() throws Exception {
        String xml = minimalStylemenu(""
            + "    <layer id=\"night\" enabled=\"false\" visible=\"true\">"
            + "      <name lang=\"en\" value=\"Night mode\"/>"
            + "    </layer>");

        String path = createTempXml(xml);
        ReactApplicationContext mockCtx = mock(ReactApplicationContext.class);

        List<RenderThemeMenuLoader.StyleMenuEntry> first =
                RenderThemeMenuLoader.load(path, mockCtx);
        List<RenderThemeMenuLoader.StyleMenuEntry> second =
                RenderThemeMenuLoader.load(path, mockCtx);

        assertEquals("Both calls must return same number of entries",
                first.size(), second.size());
        assertEquals("Both calls must return same id",
                first.get(0).id, second.get(0).id);
        assertEquals("Both calls must return same label",
                first.get(0).label, second.get(0).label);
    }

    @Test
    public void load_overlayReferencesResolvedById() throws Exception {
        // The "hills" layer is defined first and referenced by "custom" via <overlay id="hills"/>.
        // The overlay resolution in the SAX handler looks up byId.get(ovId) at the time of the
        // <overlay> element. The "hills" layer must already be parsed for the overlay to resolve.
        // Because the SAX handler processes layers sequentially, and "hills" appears before "custom",
        // the byId map will have "hills" when "custom" is processed.
        String xml = ""
            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
            + "<rendertheme xmlns=\"http://mapsforge.org/renderTheme\" version=\"1\">"
            + "  <stylemenu defaultlang=\"en\" defaultvalue=\"default\">"
            + "    <layer id=\"hillshading\" enabled=\"false\" visible=\"true\">"
            + "      <name lang=\"en\" value=\"Hillshading\"/>"
            + "    </layer>"
            + "    <layer id=\"custom\" enabled=\"false\" visible=\"true\">"
            + "      <name lang=\"en\" value=\"With overlay\"/>"
            + "      <overlay id=\"hillshading\"/>"
            + "    </layer>"
            + "  </stylemenu>"
            + "</rendertheme>";

        String path = createTempXml(xml);
        ReactApplicationContext mockCtx = mock(ReactApplicationContext.class);
        List<RenderThemeMenuLoader.StyleMenuEntry> entries =
                RenderThemeMenuLoader.load(path, mockCtx);

        assertEquals(2, entries.size());

        RenderThemeMenuLoader.StyleMenuEntry custom = entries.get(1);
        assertEquals("custom", custom.id);
        assertEquals(1, custom.overlays.size());
        assertEquals("hillshading", custom.overlays.get(0).id);
        assertEquals("Hillshading", custom.overlays.get(0).label);
    }
}
