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

export interface TopPostTemplateSourcePost {
  excerpt: string;
  publishedAt: string | null;
  reactions: number;
  comments: number;
  shares: number;
}

/**
 * AI-drafted template from the Page's best-performing posts (UC-1.5 alternate
 * flow). Nothing is saved server-side; save it with createPostTemplate.
 * When `available` is false, `reason` explains why (not enough engagement yet).
 */
export interface TopPostTemplateSuggestion {
  available: boolean;
  reason: string | null;
  name: string | null;
  caption: string | null;
  tags: string[];
  insights: string[];
  source: "facebook_page" | "dasigconnect";
  postsConsidered: number;
  topPosts: TopPostTemplateSourcePost[];
}

export function suggestTemplateFromTopPosts() {
  return api.post<TopPostTemplateSuggestion>("/ai/templates/from-top-posts");
}
