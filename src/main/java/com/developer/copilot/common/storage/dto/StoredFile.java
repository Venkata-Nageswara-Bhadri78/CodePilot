package com.developer.copilot.common.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StoredFile {

    private String storageKey;
    private String originalFilename;
    private String contentType;
    private Long fileSize;
    private String checksum;

}