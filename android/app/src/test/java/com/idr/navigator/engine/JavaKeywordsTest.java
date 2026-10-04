package com.idr.navigator.engine;
import static org.junit.Assert.*;
import com.idr.navigator.compat.JavaKeywords;
import org.junit.Test;

public class JavaKeywordsTest {
    @Test public void androidKeywordCheckPreservesJdkValidation() throws Exception {
        var reference = Class.forName("javax.lang.model.SourceVersion").getMethod("isKeyword", CharSequence.class);
        String words = "abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for goto if implements import instanceof int interface long native new package private protected public return short static strictfp super switch synchronized this throw throws transient try void volatile while true false null _ record var yield sealed permits non_sealed road_access car_average_speed in_test Class class_";
        for (String word : words.split(" ")) assertEquals(word, reference.invoke(null, word), JavaKeywords.isKeyword(word));
    }
}
