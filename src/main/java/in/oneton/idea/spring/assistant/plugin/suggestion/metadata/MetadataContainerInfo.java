package in.oneton.idea.spring.assistant.plugin.suggestion.metadata;

import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;

import static com.intellij.openapi.fileTypes.FileTypes.ARCHIVE;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Stream.of;

@Getter
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MetadataContainerInfo {

    public static final String SPRING_CONFIGURATION_METADATA_JSON = "spring-configuration-metadata.json";
    public static final String ADDITIONAL_SPRING_CONFIGURATION_METADATA_JSON = "additional-spring-configuration-metadata.json";

    /**
     * Can point to archive/directory containing the metadata file
     */
    @EqualsAndHashCode.Include
    private final String containerArchiveOrFileRef;

    @EqualsAndHashCode.Include
    private final boolean archive;

    /**
     * If containerPath points to archive, then represents the timestamp of the archive
     * else, represents the length of the generated metadata file
     */
    @EqualsAndHashCode.Include
    private final long marker;

    @Nullable
    private final String fileUrl;

    public static Stream<String> getContainerArchiveOrFileRefs(final VirtualFile fileContainer) {
        if (fileContainer.getFileType() == ARCHIVE) {
            return of(getContainerFile(fileContainer).getUrl());
        } else {
            final var metadataFile = findMetadataFile(fileContainer, SPRING_CONFIGURATION_METADATA_JSON);
            final var additionalMetadataFile = findMetadataFile(fileContainer, ADDITIONAL_SPRING_CONFIGURATION_METADATA_JSON);

            if (metadataFile == null && additionalMetadataFile == null) {
                return of(fileContainer.getUrl());
            } else {
                if (metadataFile == null) {
                    return of(additionalMetadataFile.getUrl());
                } else if (additionalMetadataFile == null) {
                    return of(metadataFile.getUrl());
                } else {
                    return of(metadataFile.getUrl(), additionalMetadataFile.getUrl());
                }
            }
        }
    }

    private static VirtualFile getContainerFile(final VirtualFile fileContainer) {
        return (fileContainer.getFileType() == ARCHIVE)
                ? requireNonNull(JarFileSystem.getInstance().getLocalVirtualFileFor(fileContainer))
                : fileContainer;
    }

    private static VirtualFile findMetadataFile(final VirtualFile root, final String metadataFileName) {
        if (!root.is(VFileProperty.SYMLINK)) {
            //noinspection UnsafeVfsRecursion
            for (final VirtualFile child : root.getChildren()) {
                if (child.getName().equals(metadataFileName)) {
                    return child;
                }
                final VirtualFile matchedFile = findMetadataFile(child, metadataFileName);
                if (matchedFile != null) {
                    return matchedFile;
                }
            }
        }
        return null;
    }

    public static Collection<MetadataContainerInfo> newInstances(final VirtualFile fileContainer) {
        final Collection<MetadataContainerInfo> containerInfos = new ArrayList<>();
        final var containerFile = getContainerFile(fileContainer);
        final boolean archive = fileContainer.getFileType() == ARCHIVE;

        final var containerInfo = newInstance(fileContainer, containerFile, SPRING_CONFIGURATION_METADATA_JSON, archive);
        containerInfos.add(containerInfo);

//        if (!archive) { TODO:Elton
            /* Even after enabling annotation processor support in intellij, for the projects with `spring-boot-configuration-processor` in classpath,
             intellij is not merging `spring-configuration-metadata.json` & the generated `additional-spring-configuration-metadata.json`.
             So lets merge these two ourselves if root is not an archive */
        final var additionalContainerInfo = newInstance(fileContainer, containerFile,
                ADDITIONAL_SPRING_CONFIGURATION_METADATA_JSON, false);
        if (Objects.nonNull(additionalContainerInfo)) {
            containerInfos.add(additionalContainerInfo);
        }
//        }

        return containerInfos;
    }

    private static MetadataContainerInfo newInstance(final VirtualFile fileContainer, final VirtualFile containerFile,
                                                     final String metadataFileName, final boolean archive) {
        final var builder = MetadataContainerInfo.builder().archive(archive);
        final var metadataFile = findMetadataFile(fileContainer, metadataFileName);

        if (Objects.nonNull(metadataFile)) {
            // since build might auto generate the metadata file in the project, its better to rely on
            return builder.fileUrl(metadataFile.getUrl())
                    .containerArchiveOrFileRef(archive ? containerFile.getUrl() : metadataFile.getUrl())
                    .marker(archive ? metadataFile.getModificationCount() : metadataFile.getModificationStamp())
                    .build();
        }

        return builder.containerArchiveOrFileRef(containerFile.getUrl())
                .marker(containerFile.getModificationCount())
                .build();
    }

    public boolean isModified(final MetadataContainerInfo other) {
        return other != null && this.marker != other.marker;
    }

    public boolean containsMetadataFile() {
        return this.fileUrl != null;
    }

    public VirtualFile getMetadataFile() {
        assert this.fileUrl != null;
        return VirtualFileManager.getInstance().findFileByUrl(this.fileUrl);
    }
}
