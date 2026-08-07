package com.jhotadhari.reactnative.mapsforge.vtm;

import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UtilsTest {

    // -----------------------------------------------------------------------
    // slugify
    // -----------------------------------------------------------------------

    @Test
    public void slugify_simpleAsciiWithSpaces() {
        assertEquals("hello-world", Utils.slugify("Hello World"));
    }

    @Test
    public void slugify_accentedCharactersRemoved() {
        // é decomposes to e + combining acute under NFD; the combining mark is stripped.
        assertEquals("cafe", Utils.slugify("café"));
    }

    @Test
    public void slugify_umlauts() {
        // ü → u, standard German umlaut
        assertEquals("uber", Utils.slugify("über"));
    }

    @Test
    public void slugify_tildeN() {
        // ñ (Spanish) → n; Ñ → n after toLowerCase
        assertEquals("nono", Utils.slugify("Ñoño"));
    }

    @Test
    public void slugify_mixedAccentsAndPunctuation() {
        // Source comment in Utils.java: "l'été, où es tu ?" → "l-ete-ou-es-tu"
        assertEquals("l-ete-ou-es-tu", Utils.slugify("l'été, où es tu ?"));
    }

    @Test
    public void slugify_punctuationOnlyInput() {
        // All chars are punctuation → all become spaces → trim → empty string
        assertEquals("", Utils.slugify("!!!"));
    }

    @Test
    public void slugify_leadingAndTrailingSpacesTrimmed() {
        // Spaces are whitespace, not punctuation — trim() removes them.
        assertEquals("leading-trailing", Utils.slugify("  leading  trailing  "));
    }

    @Test
    public void slugify_multipleSpacesCollapsed() {
        // Multiple internal spaces are collapsed to a single hyphen.
        assertEquals("a-b", Utils.slugify("a    b"));
    }

    @Test
    public void slugify_emptyString() {
        assertEquals("", Utils.slugify(""));
    }

    @Test
    public void slugify_lowercasesInput() {
        assertEquals("hello", Utils.slugify("HELLO"));
    }

    @Test
    public void slugify_numbersPreserved() {
        // Digits are neither marks nor punctuation — they pass through unchanged.
        assertEquals("abc123", Utils.slugify("abc123"));
    }

    @Test
    public void slugify_exclamationAndCommaReplacedWithHyphen() {
        // "café au lait!" → "cafe-au-lait"
        assertEquals("cafe-au-lait", Utils.slugify("café au lait!"));
    }

    @Test
    public void slugify_dotsReplacedWithSingleHyphen() {
        // "...dots..." → all dots become spaces → trim → "dots"
        assertEquals("dots", Utils.slugify("...dots..."));
    }

    // -----------------------------------------------------------------------
    // lngFromPosition — position.getDouble(0)
    // -----------------------------------------------------------------------

    @Test
    public void lngFromPosition_returnsIndexZero() {
        ReadableArray pos = mock(ReadableArray.class);
        when(pos.getDouble(0)).thenReturn(13.405);
        assertEquals(13.405, Utils.lngFromPosition(pos), 1e-9);
    }

    @Test
    public void lngFromPosition_negativeValue() {
        ReadableArray pos = mock(ReadableArray.class);
        when(pos.getDouble(0)).thenReturn(-122.419);
        assertEquals(-122.419, Utils.lngFromPosition(pos), 1e-9);
    }

    // -----------------------------------------------------------------------
    // latFromPosition — position.getDouble(1)
    // -----------------------------------------------------------------------

    @Test
    public void latFromPosition_returnsIndexOne() {
        ReadableArray pos = mock(ReadableArray.class);
        when(pos.getDouble(1)).thenReturn(52.520);
        assertEquals(52.520, Utils.latFromPosition(pos), 1e-9);
    }

    @Test
    public void latFromPosition_negativeValue() {
        ReadableArray pos = mock(ReadableArray.class);
        when(pos.getDouble(1)).thenReturn(-33.868);
        assertEquals(-33.868, Utils.latFromPosition(pos), 1e-9);
    }

    // -----------------------------------------------------------------------
    // altFromPosition — size > 2 ? getDouble(2) : null
    // -----------------------------------------------------------------------

    @Test
    public void altFromPosition_presentWhenSizeIsThree() {
        ReadableArray pos = mock(ReadableArray.class);
        when(pos.size()).thenReturn(3);
        when(pos.getDouble(2)).thenReturn(420.0);
        Double alt = Utils.altFromPosition(pos);
        assertNotNull("Altitude must not be null when size == 3", alt);
        assertEquals(420.0, alt, 1e-9);
    }

    @Test
    public void altFromPosition_presentWhenSizeIsGreaterThanThree() {
        ReadableArray pos = mock(ReadableArray.class);
        when(pos.size()).thenReturn(4);
        when(pos.getDouble(2)).thenReturn(1000.5);
        Double alt = Utils.altFromPosition(pos);
        assertNotNull(alt);
        assertEquals(1000.5, alt, 1e-9);
    }

    @Test
    public void altFromPosition_nullWhenSizeIsTwo() {
        ReadableArray pos = mock(ReadableArray.class);
        when(pos.size()).thenReturn(2);
        assertNull("Altitude must be null when size == 2", Utils.altFromPosition(pos));
    }

    @Test
    public void altFromPosition_nullWhenSizeIsOne() {
        ReadableArray pos = mock(ReadableArray.class);
        when(pos.size()).thenReturn(1);
        assertNull("Altitude must be null when size == 1", Utils.altFromPosition(pos));
    }

    @Test
    public void altFromPosition_zeroAltitudeIsReturned() {
        // Zero is a valid altitude — must not be confused with "absent".
        ReadableArray pos = mock(ReadableArray.class);
        when(pos.size()).thenReturn(3);
        when(pos.getDouble(2)).thenReturn(0.0);
        Double alt = Utils.altFromPosition(pos);
        assertNotNull("Altitude of 0.0 must not be treated as absent", alt);
        assertEquals(0.0, alt, 1e-9);
    }

    // -----------------------------------------------------------------------
    // rMapHasKey — args.hasKey(key) && !args.isNull(key)
    // -----------------------------------------------------------------------

    @Test
    public void rMapHasKey_trueWhenKeyExistsAndIsNotNull() {
        ReadableMap map = mock(ReadableMap.class);
        when(map.hasKey("foo")).thenReturn(true);
        when(map.isNull("foo")).thenReturn(false);
        assertTrue(Utils.rMapHasKey(map, "foo"));
    }

    @Test
    public void rMapHasKey_falseWhenKeyExistsButIsNull() {
        ReadableMap map = mock(ReadableMap.class);
        when(map.hasKey("bar")).thenReturn(true);
        when(map.isNull("bar")).thenReturn(true);
        assertFalse(Utils.rMapHasKey(map, "bar"));
    }

    @Test
    public void rMapHasKey_falseWhenKeyAbsent() {
        ReadableMap map = mock(ReadableMap.class);
        when(map.hasKey("baz")).thenReturn(false);
        // isNull is NOT called due to short-circuit — no stub needed.
        assertFalse(Utils.rMapHasKey(map, "baz"));
    }

    @Test
    public void rMapHasKey_differentiatesMultipleKeys() {
        ReadableMap map = mock(ReadableMap.class);
        when(map.hasKey("present")).thenReturn(true);
        when(map.isNull("present")).thenReturn(false);
        when(map.hasKey("absent")).thenReturn(false);
        when(map.hasKey("nulled")).thenReturn(true);
        when(map.isNull("nulled")).thenReturn(true);

        assertTrue(Utils.rMapHasKey(map, "present"));
        assertFalse(Utils.rMapHasKey(map, "absent"));
        assertFalse(Utils.rMapHasKey(map, "nulled"));
    }
}
