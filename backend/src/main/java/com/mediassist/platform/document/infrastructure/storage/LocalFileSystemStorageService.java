package com.mediassist.platform.document.infrastructure.storage;

import com.mediassist.platform.document.application.DocumentStorageDetails;
import com.mediassist.platform.document.application.storage.DocumentStorageOperationException;
import com.mediassist.platform.document.application.storage.DocumentStorageService;
import com.mediassist.platform.document.application.storage.InvalidDocumentFileException;
import com.mediassist.platform.document.application.storage.StoredDocumentNotFoundException;
import com.mediassist.platform.document.application.storage.StoredDocumentResource;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalFileSystemStorageService implements DocumentStorageService {

    private static final String PDF_SUFFIX = ".pdf";
    private static final String PDF_SIGNATURE = "%PDF-";

    private final Path rootLocation;
    private final Set<String> allowedExtensions;
    private final Set<String> allowedContentTypes;

    public LocalFileSystemStorageService(DocumentStorageProperties documentStorageProperties) {
        this.rootLocation = Path.of(documentStorageProperties.getDocumentsRoot()).toAbsolutePath().normalize();
        this.allowedExtensions = documentStorageProperties.getAllowedExtensions().stream()
            .map(value -> value.toLowerCase(Locale.ROOT))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.allowedContentTypes = documentStorageProperties.getAllowedContentTypes().stream()
            .map(value -> value.toLowerCase(Locale.ROOT))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @PostConstruct
    void initializeStorage() {
        createDirectories(rootLocation);
    }

    @Override
    public DocumentStorageDetails store(MultipartFile file) {
        validateFilePresence(file);

        String originalFileName = extractOriginalFileName(file);
        validatePdfExtension(originalFileName);
        validateContentType(file.getContentType());
        validatePdfSignature(file);

        StorageTarget storageTarget = createStorageTarget();
        createDirectories(storageTarget.targetPath().getParent());

        String checksum = writeFileAndComputeChecksum(file, storageTarget.targetPath());

        return new DocumentStorageDetails(
            originalFileName,
            storageTarget.storedFileName(),
            MediaType.APPLICATION_PDF_VALUE,
            file.getSize(),
            checksum,
            storageTarget.relativeStoragePath()
        );
    }

    @Override
    public StoredDocumentResource load(String storagePath) {
        Path resolvedPath = resolveStoragePath(storagePath);

        if (!Files.exists(resolvedPath) || !Files.isRegularFile(resolvedPath)) {
            throw new StoredDocumentNotFoundException("Stored document file not found");
        }

        try {
            Resource resource = new UrlResource(resolvedPath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new StoredDocumentNotFoundException("Stored document file is not readable");
            }

            return new StoredDocumentResource(resource, Files.size(resolvedPath));
        } catch (MalformedURLException exception) {
            throw new DocumentStorageOperationException("Unable to load stored document", exception);
        } catch (IOException exception) {
            throw new DocumentStorageOperationException("Unable to read stored document", exception);
        }
    }

    @Override
    public void delete(String storagePath) {
        Path resolvedPath = resolveStoragePath(storagePath);

        try {
            Files.deleteIfExists(resolvedPath);
        } catch (IOException exception) {
            throw new DocumentStorageOperationException("Unable to delete stored document", exception);
        }
    }

    private void validateFilePresence(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidDocumentFileException("Uploaded document file must not be empty");
        }
    }

    private String extractOriginalFileName(MultipartFile file) {
        String submittedName = file.getOriginalFilename();
        if (submittedName == null || submittedName.isBlank()) {
            throw new InvalidDocumentFileException("Uploaded document file must include an original filename");
        }

        String normalizedName = submittedName.replace("\\", "/").trim();
        String sanitizedName = normalizedName.substring(normalizedName.lastIndexOf('/') + 1);
        if (sanitizedName.isBlank()) {
            throw new InvalidDocumentFileException("Uploaded document file has an invalid filename");
        }

        return sanitizedName;
    }

    private void validatePdfExtension(String originalFileName) {
        String extension = extractExtension(originalFileName);
        if (!allowedExtensions.contains(extension)) {
            throw new InvalidDocumentFileException("Only PDF files are supported");
        }
    }

    private void validateContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new InvalidDocumentFileException("Uploaded document file must declare a content type");
        }

        if (!allowedContentTypes.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new InvalidDocumentFileException("Uploaded document content type must be application/pdf");
        }
    }

    private void validatePdfSignature(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] signatureBytes = inputStream.readNBytes(PDF_SIGNATURE.length());
            String signature = new String(signatureBytes, java.nio.charset.StandardCharsets.US_ASCII);
            if (!PDF_SIGNATURE.equals(signature)) {
                throw new InvalidDocumentFileException("Uploaded document file is not a valid PDF");
            }
        } catch (IOException exception) {
            throw new DocumentStorageOperationException("Unable to validate uploaded document", exception);
        }
    }

    private StorageTarget createStorageTarget() {
        LocalDate currentDate = LocalDate.now(ZoneOffset.UTC);
        String storedFileName = UUID.randomUUID() + PDF_SUFFIX;
        String relativeStoragePath = String.join(
            "/",
            String.valueOf(currentDate.getYear()),
            String.format("%02d", currentDate.getMonthValue()),
            String.format("%02d", currentDate.getDayOfMonth()),
            storedFileName
        );
        Path targetPath = resolveStoragePath(relativeStoragePath);

        return new StorageTarget(storedFileName, relativeStoragePath, targetPath);
    }

    private String writeFileAndComputeChecksum(MultipartFile file, Path targetPath) {
        java.security.MessageDigest messageDigest = createSha256Digest();

        try (
            InputStream inputStream = file.getInputStream();
            OutputStream outputStream = Files.newOutputStream(
                targetPath,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
            )
        ) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                messageDigest.update(buffer, 0, bytesRead);
                outputStream.write(buffer, 0, bytesRead);
            }

            return HexFormat.of().formatHex(messageDigest.digest());
        } catch (IOException exception) {
            deleteQuietly(targetPath);
            throw new DocumentStorageOperationException("Unable to store uploaded document", exception);
        }
    }

    private java.security.MessageDigest createSha256Digest() {
        try {
            return java.security.MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new DocumentStorageOperationException("SHA-256 digest is not available", exception);
        }
    }

    private Path resolveStoragePath(String storagePath) {
        try {
            if (storagePath == null || storagePath.isBlank()) {
                throw new InvalidDocumentFileException("Invalid document storage path");
            }

            Path relativePath = Path.of(storagePath).normalize();
            if (relativePath.isAbsolute()) {
                throw new InvalidDocumentFileException("Invalid document storage path");
            }

            Path resolvedPath = rootLocation.resolve(relativePath).normalize();

            if (!resolvedPath.startsWith(rootLocation)) {
                throw new InvalidDocumentFileException("Invalid document storage path");
            }

            return resolvedPath;
        } catch (InvalidDocumentFileException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidDocumentFileException("Invalid document storage path");
        }
    }

    private String extractExtension(String fileName) {
        int extensionSeparatorIndex = fileName.lastIndexOf('.');
        if (extensionSeparatorIndex < 0 || extensionSeparatorIndex == fileName.length() - 1) {
            return "";
        }

        return fileName.substring(extensionSeparatorIndex + 1).toLowerCase(Locale.ROOT);
    }

    private void createDirectories(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException exception) {
            throw new DocumentStorageOperationException("Unable to create document storage directories", exception);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Keep the original storage exception as the primary failure.
        }
    }

    private record StorageTarget(
        String storedFileName,
        String relativeStoragePath,
        Path targetPath
    ) {
    }
}
