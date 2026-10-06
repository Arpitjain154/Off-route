package com.idr.navigator.engine;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.junit.Test;

public class MapMatchingTest {

    @Test
    public void realBhopalRoadWithSyntheticDrift() throws Exception {
        Eskf filter = new Eskf();
        filter.initialize(23.24, 77.4, 0);
        File directory = new File("build/test-map-graph-vehicle-v1");
        directory.mkdirs();
        MapMatchAdvisor advisor = new MapMatchAdvisor(filter);
        try {
            advisor.load(
                new File("src/main/assets/maps/bhopal.osm").getAbsolutePath(),
                directory.getAbsolutePath()
            );
            String json = new String(
                Files.readAllBytes(new File("../../public/maps/route.json").toPath()),
                java.nio.charset.StandardCharsets.UTF_8
            );
            Matcher matcher = Pattern.compile("\\[([0-9.]+),\\s*([0-9.]+)\\]").matcher(json);
            List<double[]> points = new ArrayList<>();
            while (matcher.find())
                points.add(new double[] {
                    Double.parseDouble(matcher.group(1)),
                    Double.parseDouble(matcher.group(2)),
                });
            assertTrue(points.size() > 10);
            long now = 1_000_000_000L;
            int available = 0;
            for (int i = 0; i < Math.min(32, points.size() / 2); i++) {
                double[] p = points.get(i);
                now += 2_000_000_000L;
                advisor.observe(now, p[1] + 20.0 / 111320, p[0] + 15.0 / 102200);
                Measurement result = advisor.provide(now);
                if (result != null && advisor.trustScore() > .1) available++;
            }
            assertTrue(
                "Real OSM road should accept a 25m drifted track; " + advisor.status,
                available > 2
            );
        } finally {
            advisor.close();
        }
    }
}
