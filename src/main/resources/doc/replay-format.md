# Mindurka Replay Format

This documentation uses bytedoc format. Specification available [here](<https://github.com/5GameMaker/dummies-wiki/blob/master/wiki/meta/bytedoc.md>).

```bytedoc
MDR\0  << Format magic number >>
(u16be: format_version = 1)
(u16be: mindustry_major)
(u16be: mindustry_minor)
(u32be: mindustry_minor)
(Chunkv1[?]: chunks)

Chunkv1:
(u32be: timestamp << milliseconds since the start of recording >>)
(u8: chunk_kind) << invalid chunks are skipped >>
(u32be: data_size)
(byte[data_size]: data)
(u32be: data_size)

ChunkJumpTablev1: Chunkv1.{chunk_kind = 1, data = JumpTablev1}
JumpTablev1:

```