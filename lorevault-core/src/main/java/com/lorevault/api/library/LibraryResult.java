package com.lorevault.api.library;

public record LibraryResult<T>(T entity, boolean isNew) {
}
