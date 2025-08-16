package com.lorevault.api.util;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class VectorMathTest {

    @Test
    void dot_basic() {
        assertThat(VectorMath.dot(new double[]{1,2,3}, new double[]{4,5,6})).isEqualTo(32.0);
    }

    @Test
    void norm_basic() {
        assertThat(VectorMath.norm(new double[]{3,4})).isEqualTo(5.0);
    }

    @Test
    void cosine_basic() {
        double sim = VectorMath.cosineSimilarity(new double[]{1,0}, new double[]{0,1});
        assertThat(sim).isEqualTo(0.0);
        double sim2 = VectorMath.cosineSimilarity(new double[]{1,1}, new double[]{1,1});
        assertThat(sim2).isCloseTo(1.0, within(1e-12));
    }

    @Test
    void l2_basic() {
        double dist = VectorMath.l2Distance(new double[]{1,2}, new double[]{4,6});
        assertThat(dist).isEqualTo(5.0);
    }

    @Test
    void nullAndMismatched() {
        assertThat(VectorMath.dot(null, new double[]{1})).isZero();
        assertThat(VectorMath.cosineSimilarity(null, new double[]{1})).isZero();
        assertThat(VectorMath.l2Distance(null, new double[]{1})).isNaN();
        assertThat(VectorMath.dot(new double[]{1,2}, new double[]{3})).isEqualTo(3.0);
    }
}
