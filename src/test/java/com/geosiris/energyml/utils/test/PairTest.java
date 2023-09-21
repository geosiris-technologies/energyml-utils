package com.geosiris.energyml.utils.test;

import com.geosiris.energyml.utils.Pair;
import org.junit.jupiter.api.Test;

public class PairTest {

    @Test
    void test_equality(){
        assert new Pair<>("abdc", "COUCOU").equals(new Pair<>("abdc", "COUCOU"));
        assert new Pair<>("abdc", "COUCOU").equals(new Pair<>("abdc", "COUCOU"));
        assert new Pair<>(1, "COUCOU").equals(new Pair<>(1, "COUCOU"));
        assert new Pair<>(1, 2.3).equals(new Pair<>(1, 2.3));
        assert ! new Pair<>("a", "COUCOU").equals(new Pair<>("abdc", "COUCOU"));
        assert ! new Pair<>("abdc", "C").equals(new Pair<>("abdc", "COUCOU"));
        assert new Pair<>("abdc", null).equals(new Pair<>("abdc", null));
        assert ! new Pair<>("abdc", (Object) "a").equals(new Pair<>("abdc", null));
        assert ! new Pair<>("abdc", null).equals(new Pair<>("abdc", (Object) "a"));
    }
}
