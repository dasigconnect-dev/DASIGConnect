export function moveMediaItem<T>(items: T[], fromIndex: number, toIndex: number) {
  if (
    fromIndex === toIndex ||
    fromIndex < 0 ||
    toIndex < 0 ||
    fromIndex >= items.length ||
    toIndex >= items.length
  ) {
    return items;
  }

  const next = [...items];
  const [moved] = next.splice(fromIndex, 1);
  next.splice(toIndex, 0, moved);
  return next;
}

export function fileMediaKey(file: File) {
  return `local:${file.name}:${file.lastModified}:${file.size}`;
}

export function savedMediaKey(id: string) {
  return `saved:${id}`;
}
