package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dasigconnect.backend.model.dto.settings.WatermarkElementDto;
import com.dasigconnect.backend.model.dto.settings.WatermarkElementDto.Position;
import com.dasigconnect.backend.model.dto.settings.WatermarkElementDto.RatioConfig;
import com.dasigconnect.backend.service.WatermarkLayout.Box;
import com.dasigconnect.backend.service.WatermarkLayout.Format;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WatermarkLayoutTest {

    private WatermarkElementDto logo() {
        WatermarkElementDto el = new WatermarkElementDto();
        el.setType("image");
        el.setXPercent(70.0);
        el.setYPercent(70.0);
        el.setWidthPercent(20.0);
        el.setHeightPercent(20.0);
        return el;
    }

    @Test
    void nearestFormat_matchesImageProportions() {
        assertThat(WatermarkLayout.nearestFormat(1000, 1000)).isEqualTo(Format.SQUARE);
        assertThat(WatermarkLayout.nearestFormat(1200, 900)).isEqualTo(Format.STANDARD);
        assertThat(WatermarkLayout.nearestFormat(1920, 1080)).isEqualTo(Format.LANDSCAPE);
        assertThat(WatermarkLayout.nearestFormat(800, 1000)).isEqualTo(Format.SQUARE);
        assertThat(WatermarkLayout.nearestFormat(0, 0)).isEqualTo(Format.SQUARE);
    }

    @Test
    void widerCanvas_keepsPixelSizeAndHeight() {
        WatermarkElementDto el = logo();

        Box square = WatermarkLayout.resolveBox(el, Format.SQUARE, 1000, 1000);
        Box landscape = WatermarkLayout.resolveBox(el, Format.LANDSCAPE, 1778, 1000);

        assertThat(landscape.width()).isEqualTo(square.width());
        assertThat(landscape.height()).isEqualTo(square.height());
        // Untouched formats keep the element at the same pixel spot as in 1:1.
        assertThat(landscape.x()).isEqualTo(square.x());
        assertThat(landscape.y()).isEqualTo(square.y());
    }

    @Test
    void savedFormatPosition_onlyAffectsThatFormat() {
        WatermarkElementDto el = logo();
        el.setLayouts(Map.of("16:9", new Position(85.0, 10.0)));

        Box square = WatermarkLayout.resolveBox(el, Format.SQUARE, 1000, 1000);
        Box standard = WatermarkLayout.resolveBox(el, Format.STANDARD, 1333, 1000);
        Box landscape = WatermarkLayout.resolveBox(el, Format.LANDSCAPE, 1778, 1000);

        assertThat(square.x()).isEqualTo(700);
        assertThat(square.y()).isEqualTo(700);
        assertThat(standard.y()).isEqualTo(700);
        assertThat(landscape.x()).isEqualTo(Math.round(1778 * 0.85));
        assertThat(landscape.y()).isEqualTo(100);
    }

    @Test
    void perRatioConfigurations_resolveIndependently() {
        WatermarkElementDto el = logo();
        RatioConfig r169 = new RatioConfig(
                85.0, 10.0, 30.0, 25.0, 0.5, 2, false,
                "#FF0000", null, null, null, null, null, null
        );
        RatioConfig r11 = new RatioConfig(
                70.0, 70.0, 20.0, 20.0, 1.0, 0, false,
                "#FFFFFF", null, null, null, null, null, null
        );
        el.setRatios(Map.of("1:1", r11, "16:9", r169));

        var cfg11 = WatermarkLayout.resolveConfig(el, Format.SQUARE, 0);
        var cfg43 = WatermarkLayout.resolveConfig(el, Format.STANDARD, 0);
        var cfg169 = WatermarkLayout.resolveConfig(el, Format.LANDSCAPE, 0);

        // 1:1 uses its ratio config
        assertThat(cfg11.xPercent()).isEqualTo(70.0);
        assertThat(cfg11.order()).isEqualTo(0);
        assertThat(cfg11.opacity()).isEqualTo(1.0);
        assertThat(cfg11.textColor()).isEqualTo("#FFFFFF");

        // 4:3 is untouched, falls back to 1:1 scaled
        assertThat(cfg43.xPercent()).isEqualTo(70.0 / (4.0 / 3.0));
        assertThat(cfg43.order()).isEqualTo(0);
        assertThat(cfg43.opacity()).isEqualTo(1.0);

        // 16:9 uses its customized ratio config
        assertThat(cfg169.xPercent()).isEqualTo(85.0);
        assertThat(cfg169.yPercent()).isEqualTo(10.0);
        assertThat(cfg169.widthPercent()).isEqualTo(30.0);
        assertThat(cfg169.heightPercent()).isEqualTo(25.0);
        assertThat(cfg169.opacity()).isEqualTo(0.5);
        assertThat(cfg169.order()).isEqualTo(2);
        assertThat(cfg169.textColor()).isEqualTo("#FF0000");
    }

    @Test
    void fullWidthElement_fillsEveryFormat() {
        WatermarkElementDto bar = new WatermarkElementDto();
        bar.setType("shape");
        bar.setWidthPercent(WatermarkLayout.FULL_WIDTH_PERCENT);
        bar.setHeightPercent(10.0);
        bar.setYPercent(90.0);

        assertThat(WatermarkLayout.resolveBox(bar, Format.SQUARE, 1000, 1000).width()).isEqualTo(1000);
        assertThat(WatermarkLayout.resolveBox(bar, Format.LANDSCAPE, 1778, 1000).width()).isEqualTo(1778);
    }

    @Test
    void elementIsKeptInsideTheCanvas() {
        WatermarkElementDto el = logo();
        el.setXPercent(95.0);
        el.setYPercent(99.0);

        Box box = WatermarkLayout.resolveBox(el, Format.SQUARE, 1000, 1000);

        assertThat(box.x() + box.width()).isLessThanOrEqualTo(1000);
        assertThat(box.y() + box.height()).isLessThanOrEqualTo(1000);
    }

    @Test
    void cornerAnchorsKeepTheSameInsetOnPortraitAndPanoramaImages() {
        WatermarkElementDto el = logo();
        el.setXPercent(3.0);
        el.setYPercent(4.0);

        for (String anchor : List.of("top-left", "top-right", "bottom-left", "bottom-right")) {
            el.setAnchor(anchor);
            assertInset(WatermarkLayout.resolveBox(el, Format.SQUARE, 800, 1200), 800, 1200, anchor);
            assertInset(WatermarkLayout.resolveBox(el, Format.LANDSCAPE, 2400, 600), 2400, 600, anchor);
        }
    }

    private void assertInset(Box box, int imageWidth, int imageHeight, String anchor) {
        double horizontalInset = anchor.endsWith("right")
                ? (double) (imageWidth - box.x() - box.width()) / imageWidth
                : (double) box.x() / imageWidth;
        double verticalInset = anchor.startsWith("bottom")
                ? (double) (imageHeight - box.y() - box.height()) / imageHeight
                : (double) box.y() / imageHeight;
        assertThat(horizontalInset).isCloseTo(0.03, org.assertj.core.data.Offset.offset(0.002));
        assertThat(verticalInset).isCloseTo(0.04, org.assertj.core.data.Offset.offset(0.002));
    }

    @Test
    void elementJson_roundTripsPositionsLayoutsRatiosAndHidden() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        WatermarkElementDto el = logo();
        el.setHidden(true);
        el.setAnchor("bottom-right");
        el.setLayouts(Map.of("4:3", new Position(12.5, 30.0)));
        el.setRatios(Map.of("16:9", new RatioConfig(
                50.0, 20.0, 15.0, 15.0, 0.8, 1, false,
                "#FFCC00", null, null, 4.0, "700", "normal", "Inter"
        )));

        String json = mapper.writeValueAsString(el);
        WatermarkElementDto parsed = mapper.readValue(json, WatermarkElementDto.class);

        assertThat(json).contains("\"xPercent\"").contains("\"yPercent\"").doesNotContain("\"xpercent\"");
        assertThat(json).contains("\"ratios\"").contains("\"16:9\"");
        assertThat(parsed.getXPercent()).isEqualTo(70.0);
        assertThat(parsed.getYPercent()).isEqualTo(70.0);
        assertThat(parsed.getAnchor()).isEqualTo("bottom-right");
        assertThat(parsed.isHiddenElement()).isTrue();
        assertThat(parsed.getLayouts()).containsEntry("4:3", new Position(12.5, 30.0));
        assertThat(parsed.getRatios()).containsKey("16:9");
        RatioConfig r169 = parsed.getRatios().get("16:9");
        assertThat(r169.xPercent()).isEqualTo(50.0);
        assertThat(r169.opacity()).isEqualTo(0.8);
        assertThat(r169.order()).isEqualTo(1);
        assertThat(r169.textColor()).isEqualTo("#FFCC00");
    }
}
