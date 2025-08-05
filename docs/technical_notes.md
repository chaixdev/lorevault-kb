# LoreVault Technical Notes

## Architecture Decision: Gemma 3B for Local Entity Extraction

### Decision Summary
**Date:** August 5, 2025  
**Decision:** Replace langextract with Gemma 3B for local entity extraction and pre-processing.

### Context
The original architecture relied on `langextract`, a deterministic NLP library, for local entity extraction. After evaluation, we determined that a lightweight AI model would provide better accuracy and flexibility while maintaining local execution benefits.

### Decision Rationale

#### Why Gemma 3B?
1. **Optimal Size/Performance Ratio:** At 3 billion parameters, Gemma 3B provides strong NLP capabilities while remaining lightweight enough for local deployment
2. **Cost Effectiveness:** Local execution eliminates per-request API costs for the high-volume entity extraction task
3. **Inference Speed:** Modern quantized versions can process text chunks in under 1 second
4. **Fine-tuning Potential:** Can be customized for domain-specific entity recognition

#### Technical Advantages
- **Better Context Understanding:** Unlike rule-based extraction, AI models understand context and can handle ambiguous references
- **Flexibility:** Can handle multiple entity types and complex text structures
- **Consistency:** Provides more reliable entity boundary detection than regex-based approaches
- **Scalability:** Performance scales with hardware (CPU/GPU) rather than algorithmic complexity

### Implementation Plan

#### Phase 1: Integration
- Deploy Gemma 3B using ONNX Runtime within Spring Boot application
- Create `GemmaClient` service for model interaction
- Implement entity extraction prompts optimized for the 3B model size

#### Phase 2: Optimization
- Implement model quantization for faster inference
- Add GPU acceleration support for high-throughput scenarios
- Create custom fine-tuning pipeline for domain-specific improvements

#### Phase 3: Fallback & Monitoring
- Implement graceful fallback to external APIs if local model fails
- Add performance monitoring and quality metrics
- Create A/B testing framework for model improvements

### Risk Mitigation

#### Performance Risks
- **Mitigation:** Extensive benchmarking and performance testing before production deployment
- **Fallback:** External API fallback ensures system reliability

#### Quality Risks
- **Mitigation:** Comprehensive testing suite with known entity extraction scenarios
- **Monitoring:** Real-time quality metrics and human review queues

#### Resource Risks
- **Mitigation:** Containerized deployment with resource limits and auto-scaling
- **Optimization:** Model quantization and efficient inference libraries

### Success Metrics
- **Cost Reduction:** >90% reduction in entity extraction costs compared to external API-only approach
- **Performance:** <2 second average processing time per text chunk
- **Quality:** >95% accuracy on entity extraction benchmark tests
- **Reliability:** <1% fallback rate to external APIs

### Alternative Considered
- **langextract:** Deterministic but limited context understanding
- **External APIs only:** High cost and latency for high-volume processing
- **Larger local models:** Resource intensive, slower inference times

### Next Steps
1. Set up Gemma 3B development environment
2. Create proof-of-concept entity extraction service
3. Benchmark performance against current external API approach
4. Implement full integration with existing pipeline
