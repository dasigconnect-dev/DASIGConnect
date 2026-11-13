package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dasigconnect.backend.model.dto.settings.WatermarkElementDto;
import com.dasigconnect.backend.model.dto.settings.WatermarkElementDto.RatioConfig;
import com.dasigconnect.backend.service.WatermarkLayout.Box;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WatermarkLayoutTest {

    private WatermarkElementDto image(String anchor) {
        WatermarkElementDto element = new WatermarkElementDto();
        element.setType("image");
        element.setAnchor(anchor);
        element.setXPercent(3.0);
        element.setYPercent(4.0);
        element.setWidthPercent(18.0);
        element.setHeightPercent(14.0);
        return element;
    }

    @Test
    void squareReferenceKeepsElementSizeAcrossPhotoRatios() {
        WatermarkElementDto element = image("bottom-right");
        Box square = WatermarkLayout.resolveBox(element, 1000, 1000);
        Box slightlyWide = WatermarkLayout.resolveBox(element, 1100, 1000);

        assertThat(slightlyWide.width()).isEqualTo(square.width()).isEqualTo(180);
        assertThat(slightlyWide.height()).isEqualTo(square.height()).isEqualTo(140);
        assertThat(square.x()).isEqualTo(1000 - 30 - 180);
        assertThat(slightlyWide.x()).isEqualTo(1100 - 30 - 180);
        assertThat(slightlyWide.y()).isEqualTo(square.y());
    }

    @Test
    void cornerInsetsScaleWithTheShorterSide() {
        for (String anchor : List.of("top-left", "top-right", "bottom-left", "bottom-right")) {
            WatermarkElementDto element = image(anchor);
            assertInset(WatermarkLayout.resolveBox(element, 1100, 1000), 1100, 1000, 30, 40);
            assertInset(WatermarkLayout.resolveBox(element, 800, 1200), 800, 1200, 24, 32);
        }
    }

    @Test
    void topAndBottomBarsStretchAcrossTheActualImage() {
        for (int[] size : List.of(new int[] {1000, 1000}, new int[] {1100, 1000}, new int[] {800, 1200})) {
            for (String anchor : List.of("top", "bottom")) {
                WatermarkElementDto bar = new WatermarkElementDto();
                bar.setType("shape");
                bar.setShapeType("gradient");
                bar.setAnchor(anchor);
                bar.setWidthPercent(100.0);
                bar.setHeightPercent(14.0);

                Box box = WatermarkLayout.resolveBox(bar, size[0], size[1]);
                int shorterSide = Math.min(size[0], size[1]);
                assertThat(box.x()).isZero();
                assertThat(box.width()).isEqualTo(size[0]);
                assertThat(box.height()).isEqualTo(Math.round(shorterSide * 0.14f));
                assertThat("top".equals(anchor) ? box.y() : box.y() + box.height())
                        .isEqualTo("top".equals(anchor) ? 0 : size[1]);
            }
        }
    }

    @Test
    void legacyFreeformPositionsAreConvertedToTheNearestCorner() {
        WatermarkElementDto element = new WatermarkElementDto();
        element.setType("image");
        element.setXPercent(78.0);
        element.setYPercent(82.0);
        element.setWidthPercent(18.0);
        element.setHeightPercent(14.0);

        WatermarkLayout.ResolvedConfig config = WatermarkLayout.resolveConfig(element, 0);
        Box box = WatermarkLayout.resolveBox(config, 1000, 1000);

        assertThat(config.anchor()).isEqualTo("bottom-right");
        assertThat(box.x()).isEqualTo(780);
        assertThat(box.y()).isEqualTo(820);
    }

    @Test
    void legacyFormatOverridesDoNotChangeCurrentSquareReferenceLayout() {
        WatermarkElementDto element = image("bottom-right");
        element.setRatios(Map.of("16:9", new RatioConfig(
                40.0, 10.0, 60.0, 50.0, 0.4, 2, false,
                "#FF0000", null, null, null, null, null, null
        )));

        Box box = WatermarkLayout.resolveBox(element, 1100, 1000);

        assertThat(box.width()).isEqualTo(180);
        assertThat(box.height()).isEqualTo(140);
        assertThat(box.x()).isEqualTo(890);
    }

    @Test
    void elementJsonKeepsLegacyDataReadableAndPersistsLayerOrder() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        WatermarkElementDto element = image("bottom-right");
        element.setOrder(4);
        element.setRatios(Map.of("16:9", new RatioConfig(
                50.0, 20.0, 15.0, 15.0, 0.8, 1, false,
                "#FFCC00", null, null, 4.0, "700", "normal", "Inter"
        )));

        String json = mapper.writeValueAsString(element);
        WatermarkElementDto parsed = mapper.readValue(json, WatermarkElementDto.class);

        assertThat(parsed.getAnchor()).isEqualTo("bottom-right");
        assertThat(parsed.getOrder()).isEqualTo(4);
        assertThat(parsed.getRatios()).containsKey("16:9");
    }

    private void assertInset(Box box, int canvasWidth, int canvasHeight, int expectedHorizontal, int expectedVertical) {
        int rightInset = canvasWidth - box.x() - box.width();
        int bottomInset = canvasHeight - box.y() - box.height();
        assertThat(Math.min(box.x(), rightInset)).isEqualTo(expectedHorizontal);
        assertThat(Math.min(box.y(), bottomInset)).isEqualTo(expectedVertical);
    }
}
