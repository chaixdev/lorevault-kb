# LoreVault Refactor Tickets Index



**Epic**: Service Consolidation & Complexity Reduction  

**Total Tickets**: 19  

**Estimated Effort**: 3-4 weeks  

**Goal**: Reduce 15+ micro-services to 4-6 focused business services



## Ticket Overview



| Ticket | Title | Phase | Priority | Effort | Risk | Dependencies |

|--------|-------|-------|----------|--------|------|--------------|

| [LVREF001](./LVREF001.md) | Inline HashService into Domain Entities | 1 | High | 2h | Low | None |

| [LVREF002](./LVREF002.md) | Move PromptLoaderService into LLM Adapters | 1 | Medium | 4h | Low | None |

| [LVREF003](./LVREF003.md) | Clean Up Unnecessary Mapper Services | 1 | Low | 2h | Low | None |

| [LVREF004](./LVREF004.md) | Create Consolidated IngestionJobService | 2 | High | 1d | Medium | 001-003 |

| [LVREF005](./LVREF005.md) | Absorb Validation Logic into Main Service | 2 | High | 4h | Low | 004 |

| [LVREF006](./LVREF006.md) | Merge Workflow Orchestration | 2 | High | 6h | Medium | 005 |

| [LVREF007](./LVREF007.md) | Consolidate Ingestion Service Tests | 2 | Medium | 1d | Low | 006 |

| [LVREF008](./LVREF008.md) | Create ContentProcessingService Foundation | 3 | High | 1d | Medium | Phase 2 |

| LVREF009 | Merge Scene Detection and Persistence | 3 | High | 2d | High | 008 |

| LVREF010 | Integrate Chunking and Embedding Workflows | 3 | High | 2d | High | 009 |

| LVREF011 | Consolidate Scene Coordination Logic | 3 | Medium | 1d | Medium | 010 |

| LVREF012 | Content Processing Integration Tests | 3 | Medium | 1d | Low | 011 |

| [LVREF013](./LVREF013.md) | Merge Health Check Services | 4 | Medium | 4h | Low | None* |

| LVREF014 | System Health Integration Tests | 4 | Low | 2h | Low | 013 |

| LVREF015 | Remove Unused Interfaces and Abstractions | 5 | Low | 4h | Low | All phases |

| LVREF016 | Update Architecture Tests | 5 | Medium | 2h | Low | 015 |

| [LVREF017](./LVREF017.md) | Comprehensive Integration Testing | 5 | High | 1d | Low | 016 |

| [LVREF018](./LVREF018.md) | Documentation Updates | 5 | Medium | 4h | Low | 017 |

| [LVREF019](./LVREF019.md) | Add Minimal OpenAPI Documentation Support | 4 | Low | 2h | Low | None* |



*Can run parallel to Phase 3



## Phase Breakdown



### Phase 1: Eliminate Utility Services (1 week, Low Risk)

**Goal**: Remove fake service boundaries for simple utilities



- **LVREF001**: Inline HashService into Domain Entities

- **LVREF002**: Move PromptLoaderService into LLM Adapters  

- **LVREF003**: Clean Up Unnecessary Mapper Services



**Benefits**: Immediate simplification, reduced indirection



### Phase 2: Consolidate Ingestion Services (1 week, Medium Risk)

**Goal**: Merge ingestion service cluster into unified business service



- **LVREF004**: Create consolidated IngestionJobService

- **LVREF005**: Absorb validation logic into main service

- **LVREF006**: Merge workflow orchestration

- **LVREF007**: Consolidate ingestion service tests



**Result**: Single `IngestionService` handling complete chapter submission workflow



### Phase 3: Consolidate Content Processing (2 weeks, High Risk)

**Goal**: Unify scene detection, chunking, and embedding into coherent service



- **LVREF008**: Create ContentProcessingService foundation  

- **LVREF009**: Merge scene detection + persistence operations

- **LVREF010**: Integrate chunking and embedding workflows

- **LVREF011**: Consolidate coordinate localization logic

- **LVREF012**: Content processing integration tests



**Result**: Single `ContentProcessingService` handling complete text-to-structured-content pipeline



### Phase 4: Consolidate System Services (3 days, Low Risk)

**Goal**: Unify health checking and system monitoring



- **LVREF013**: Merge all health check services

- **LVREF014**: System health integration tests



**Result**: Single `SystemHealthService` for complete system monitoring



### Phase 5: Final Cleanup & Testing (2 days, Low Risk)

**Goal**: Polish and validation



- **LVREF015**: Remove unused interfaces and abstractions

- **LVREF016**: Update architecture tests  

- **LVREF017**: Comprehensive integration testing

- **LVREF018**: Documentation updates



## Target Architecture



After completing all tickets:



```java

// 4 focused business services (down from 15+)

@Service IngestionService           // "Submit and process chapters"

@Service ContentProcessingService   // "Convert text to structured content"  

@Service SearchService             // "Find and retrieve content" (existing)

@Service SystemHealthService       // "Monitor system status"

```



## Implementation Guidelines



### Ticket Execution Order



1. **Phase 1 must be completed first** - establishes foundation

2. **Phase 2 depends on Phase 1** - builds on utility elimination

3. **Phase 3 depends on Phase 2** - needs ingestion consolidation complete

4. **Phase 4 can run parallel to Phase 3** - independent system services

5. **Phase 5 requires all other phases** - final validation and cleanup



### Risk Management



**High-Risk Tickets**: LVREF009, LVREF010 (Complex AI integration)

**Medium-Risk Tickets**: LVREF004, LVREF006, LVREF008, LVREF011 (Service consolidation)

**Low-Risk Tickets**: All others (Simple moves and utilities)



### Testing Strategy



- **After each ticket**: Run related tests to ensure no regression

- **After each phase**: Run integration tests for that functional area

- **After all phases**: Run comprehensive end-to-end validation (LVREF017)



### Rollback Strategy



Each ticket includes specific rollback plans. Phases can be rolled back independently if needed.



## Success Metrics



**Quantitative Goals**:

- **Service Count**: 15+ → 4-6 services ✓

- **Test Complexity**: Reduce mock objects by ~70% ✓  

- **Line Count**: 10-15% reduction through consolidation ✓

- **Service Dependencies**: 80% reduction in service-to-service calls ✓



**Qualitative Goals**:

- **Developer Experience**: Easier navigation, clearer service purposes ✓

- **Debugging**: Simpler call stacks, fewer indirection layers ✓

- **Feature Development**: Faster iteration, clearer boundaries ✓

- **Test Maintainability**: Focus on business behavior, not service choreography ✓



---


