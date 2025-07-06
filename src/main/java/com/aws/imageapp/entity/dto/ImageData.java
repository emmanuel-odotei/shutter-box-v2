package com.aws.imageapp.entity.dto;

public record ImageData(
        String url,
        String fileName,
        String key,
        String description
) {
}
