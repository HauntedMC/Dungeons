package nl.hauntedmc.dungeons.persistence;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/** Additive repository schema. Published rows are written only through the JDBC protocol. */
public final class RepoEntities {
    private RepoEntities() {}

    @Entity(name = "DungeonRepoEntry")
    @Table(
            name = "dungeon_repo_entry",
            indexes = {@Index(name = "uq_repo_name", columnList = "repo_name", unique = true)})
    public static class Entry {
        public Entry() {}

        @Id
        @Column(name = "dungeon_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String dungeon_id;

        @Column(name = "repo_name", nullable = false, columnDefinition = "VARCHAR(128)")
        protected String repo_name;

        @Column(name = "head_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String head_id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "dungeon_id",
                            referencedColumnName = "dungeon_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_repo_entry_0"))
        protected DungeonEntities.Definition reference0;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "head_id",
                            referencedColumnName = "release_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_repo_entry_head"))
        protected Release head;
    }

    @Entity(name = "DungeonRepoRelease")
    @Table(
            name = "dungeon_repo_release",
            indexes = {
                @Index(
                        name = "uq_repo_version",
                        columnList = "dungeon_id,major,minor,patch",
                        unique = true),
                @Index(name = "ix_repo_stage", columnList = "state,expires_at", unique = false)
            })
    public static class Release {
        public Release() {}

        @Id
        @Column(name = "release_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String release_id;

        @Column(name = "dungeon_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String dungeon_id;

        @Column(name = "parent_id", nullable = true, columnDefinition = "CHAR(36)")
        protected String parent_id;

        @Column(name = "state", nullable = false, columnDefinition = "VARCHAR(16)")
        protected String state;

        @Column(name = "major", nullable = true, columnDefinition = "INT")
        protected Integer major;

        @Column(name = "minor", nullable = true, columnDefinition = "INT")
        protected Integer minor;

        @Column(name = "patch", nullable = true, columnDefinition = "INT")
        protected Integer patch;

        @Column(name = "content_hash", nullable = false, columnDefinition = "CHAR(64)")
        protected String content_hash;

        @Column(name = "format_version", nullable = false, columnDefinition = "INT")
        protected Integer format_version;

        @Column(name = "minecraft_version", nullable = false, columnDefinition = "VARCHAR(64)")
        protected String minecraft_version;

        @Column(name = "actor", nullable = false, columnDefinition = "VARCHAR(128)")
        protected String actor;

        @Column(name = "server_id", nullable = false, columnDefinition = "VARCHAR(96)")
        protected String server_id;

        @Column(name = "boot_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String boot_id;

        @Column(name = "message", nullable = false, columnDefinition = "VARCHAR(1024)")
        protected String message;

        @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(3)")
        protected LocalDateTime created_at;

        @Column(name = "expires_at", nullable = false, columnDefinition = "DATETIME(3)")
        protected LocalDateTime expires_at;

        @Column(name = "file_count", nullable = false, columnDefinition = "INT")
        protected Integer file_count;

        @Column(name = "total_bytes", nullable = false, columnDefinition = "BIGINT")
        protected Long total_bytes;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "dungeon_id",
                            referencedColumnName = "dungeon_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_repo_release_0"))
        protected DungeonEntities.Definition reference0;
    }

    @Entity(name = "DungeonRepoFile")
    @Table(
            name = "dungeon_repo_file",
            indexes = {
                @Index(name = "uq_repo_path", columnList = "release_id,path_hash", unique = true),
                @Index(name = "ix_repo_manifest", columnList = "release_id,file_no")
            })
    @IdClass(File.Key.class)
    public static class File {
        public File() {}

        @Id
        @Column(name = "release_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String release_id;

        @Id
        @Column(name = "file_no", nullable = false, columnDefinition = "INT")
        protected Integer file_no;

        @Column(name = "path", nullable = false, columnDefinition = "VARCHAR(1024)")
        protected String path;

        @Column(name = "path_hash", nullable = false, columnDefinition = "CHAR(64)")
        protected String path_hash;

        @Column(name = "file_hash", nullable = false, columnDefinition = "CHAR(64)")
        protected String file_hash;

        @Column(name = "file_bytes", nullable = false, columnDefinition = "BIGINT")
        protected Long file_bytes;

        @Column(name = "chunk_count", nullable = false, columnDefinition = "INT")
        protected Integer chunk_count;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "release_id",
                            referencedColumnName = "release_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_repo_file_0"))
        protected Release reference0;

        public static class Key implements Serializable {
            private static final long serialVersionUID = 1L;
            public String release_id;
            public Integer file_no;

            public Key() {}

            @Override
            public boolean equals(Object other) {
                return other instanceof Key key
                        && Objects.equals(release_id, key.release_id)
                        && Objects.equals(file_no, key.file_no);
            }

            @Override
            public int hashCode() {
                return Objects.hash(release_id, file_no);
            }
        }
    }

    @Entity(name = "DungeonRepoChunk")
    @Table(name = "dungeon_repo_chunk")
    public static class Chunk {
        public Chunk() {}

        @Id
        @Column(name = "chunk_hash", nullable = false, columnDefinition = "CHAR(64)")
        protected String chunk_hash;

        @Column(name = "codec", nullable = false, columnDefinition = "VARCHAR(8)")
        protected String codec;

        @Column(name = "raw_bytes", nullable = false, columnDefinition = "INT")
        protected Integer raw_bytes;

        @Column(name = "payload", nullable = false, columnDefinition = "MEDIUMBLOB")
        protected byte[] payload;

        @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(3)")
        protected LocalDateTime created_at;
    }

    @Entity(name = "DungeonRepoFileChunk")
    @Table(
            name = "dungeon_repo_file_chunk",
            indexes = {
                @Index(name = "ix_repo_chunk_ref", columnList = "chunk_hash", unique = false),
                @Index(name = "ix_repo_file_chunks", columnList = "release_id,file_no,chunk_no")
            })
    @IdClass(FileChunk.Key.class)
    public static class FileChunk {
        public FileChunk() {}

        @Id
        @Column(name = "release_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String release_id;

        @Id
        @Column(name = "file_no", nullable = false, columnDefinition = "INT")
        protected Integer file_no;

        @Id
        @Column(name = "chunk_no", nullable = false, columnDefinition = "INT")
        protected Integer chunk_no;

        @Column(name = "chunk_hash", nullable = false, columnDefinition = "CHAR(64)")
        protected String chunk_hash;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "chunk_hash",
                            referencedColumnName = "chunk_hash",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_repo_filechunk_0"))
        protected Chunk reference0;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "file_no",
                            referencedColumnName = "file_no",
                            insertable = false,
                            updatable = false),
                    @JoinColumn(
                            name = "release_id",
                            referencedColumnName = "release_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_repo_file_chunk_file"))
        protected File file;

        public static class Key implements Serializable {
            private static final long serialVersionUID = 1L;
            public String release_id;
            public Integer file_no;
            public Integer chunk_no;

            public Key() {}

            @Override
            public boolean equals(Object other) {
                return other instanceof Key key
                        && Objects.equals(release_id, key.release_id)
                        && Objects.equals(file_no, key.file_no)
                        && Objects.equals(chunk_no, key.chunk_no);
            }

            @Override
            public int hashCode() {
                return Objects.hash(release_id, file_no, chunk_no);
            }
        }
    }
}
