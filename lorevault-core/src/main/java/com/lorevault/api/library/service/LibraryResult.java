package com.lorevault.api.library.service;

public record LibraryResult<T>(T entity, boolean isNew) {
}
