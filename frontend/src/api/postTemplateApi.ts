import { api } from "./authApi";

export interface PostTemplate {
  id: string;
  name: string;
  target: string;
  category: string;
  caption: string;
  tags: string[];
  sourceSubmissionId?: string | null;
  createdAt?: string;
  custom?: boolean;
}

export interface PostTemplatePayload {
  name: string;
  target?: string;
  category?: string;
  caption: string;
  tags?: string[];
  sourceSubmissionId?: string | null;
  institutionId?: string | null;
}

export function listPostTemplates(signal?: AbortSignal) {
  return api.get<PostTemplate[]>("/post-templates", { signal });
}

export function createPostTemplate(payload: PostTemplatePayload) {
  return api.post<PostTemplate>("/post-templates", payload);
}

export function deletePostTemplate(id: string) {
  return api.delete(`/post-templates/${id}`);
}
