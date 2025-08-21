package com.lorevault.api.testutil.fakes;

import com.lorevault.api.application.port.EmbeddingPort;

import java.util.ArrayList;
import java.util.List;

/**
 * Fake EmbeddingPort for unit/service tests.
 * Produces deterministic vectors from input text using a simple hash-based generator.
 */
public class FakeEmbeddingPort implements EmbeddingPort {

	private final String modelId;
	private final int dimension;

	public FakeEmbeddingPort() {
		this("fake-embedding-1", 128);
	}

	public FakeEmbeddingPort(String modelId, int dimension) {
		this.modelId = modelId;
		this.dimension = dimension;
	}

	@Override
	public double[] embed(String text) {
		return deterministicVector(text, dimension);
	}

	@Override
	public List<double[]> embedBatch(List<String> texts) {
		List<double[]> out = new ArrayList<>(texts.size());
		for (String t : texts) {
			out.add(embed(t));
		}
		return out;
	}

	@Override
	public String getModelId() {
		return modelId;
	}

	@Override
	public int getDimension() {
		return dimension;
	}

	private static double[] deterministicVector(String text, int dim) {
		// Stable seed from text
		int seed = (text == null ? 0 : text.hashCode());
		double[] v = new double[dim];
		for (int i = 0; i < dim; i++) {
			// Simple bounded pseudo-random but deterministic function
			double val = Math.sin(seed + i * 0.13) * Math.cos(seed * 0.17 + i);
			v[i] = val; // already in [-1,1]
		}
		return v;
	}
}

