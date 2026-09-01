import type { MediaAlbum } from "../../api/mediaApi";

/** Flat album list → depth-indented rows for "move to folder" selects. */
export function buildAlbumOptions(albums: MediaAlbum[]): Array<{ id: string; label: string }> {
  const byParent = new Map<string | null, MediaAlbum[]>();
  for (const album of albums) {
    const key = album.parentAlbumId ?? null;
    const bucket = byParent.get(key) ?? [];
    bucket.push(album);
    byParent.set(key, bucket);
  }
  for (const bucket of byParent.values()) {
    bucket.sort((a, b) => a.name.localeCompare(b.name));
  }
  const rows: Array<{ id: string; label: string }> = [];
  const walk = (parentId: string | null, depth: number) => {
    for (const album of byParent.get(parentId) ?? []) {
      rows.push({ id: album.id, label: `${"  ".repeat(depth)}${depth > 0 ? "└ " : ""}${album.name}` });
      walk(album.id, depth + 1);
    }
  };
  walk(null, 0);
  return rows;
}

/** Ids of an album plus every album nested under it — used to block invalid moves. */
export function albumSubtreeIds(albumId: string, albums: MediaAlbum[]): Set<string> {
  const blocked = new Set<string>([albumId]);
  let grew = true;
  while (grew) {
    grew = false;
    for (const a of albums) {
      if (a.parentAlbumId && blocked.has(a.parentAlbumId) && !blocked.has(a.id)) {
        blocked.add(a.id);
        grew = true;
      }
    }
  }
  return blocked;
}
