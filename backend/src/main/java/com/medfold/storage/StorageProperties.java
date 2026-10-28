package com.medfold.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/** Where uploaded files are kept. {@code root} is resolved against the working directory. */
@Validated
@ConfigurationProperties(prefix = "medfold.storage")
public record StorageProperties(@NotBlank @DefaultValue("../data/uploads") String root) {
}
