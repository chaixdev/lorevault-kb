package com.lorevault.api.library.application;

public record LibraryResult<T>(T entity, boolean isNew) {
}
