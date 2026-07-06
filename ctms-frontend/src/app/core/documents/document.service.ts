import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface DocumentVersionResponse {
  id: number;
  documentId: number;
  documentTitle: string;
  versionNumber: number;
  fileName: string;
  contentType: string | null;
  sizeBytes: number;
  checksumSha256: string;
  effectiveDate: string | null;
  status: 'DRAFT' | 'PENDING_REVIEW' | 'PENDING_APPROVAL' | 'CURRENT' | 'REJECTED' | 'ARCHIVED';
  uploadedByUsername: string;
  uploadedAt: string;
}

export interface DocumentResponse {
  id: number;
  title: string;
  category: string | null;
  ownerUsername: string | null;
  studyId: number | null;
  studyCode: string | null;
  subjectId: number | null;
  subjectCode: string | null;
  currentVersion: DocumentVersionResponse | null;
  createdAt: string;
  updatedAt: string;
}

export interface DocumentReviewResponse {
  id: number;
  stage: 'REVIEW' | 'APPROVAL';
  action: 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'CHANGES_REQUESTED';
  comment: string | null;
  actedByUsername: string;
  actedAt: string;
  signed: boolean;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class DocumentService {
  constructor(private readonly http: HttpClient) {}

  list(page = 0, size = 20): Observable<Page<DocumentResponse>> {
    return this.http.get<Page<DocumentResponse>>('/api/documents', { params: { page, size } });
  }

  get(id: number): Observable<DocumentResponse> {
    return this.http.get<DocumentResponse>(`/api/documents/${id}`);
  }

  listBySubject(subjectId: number): Observable<DocumentResponse[]> {
    return this.http.get<DocumentResponse[]>(`/api/documents/by-subject/${subjectId}`);
  }

  versions(id: number): Observable<DocumentVersionResponse[]> {
    return this.http.get<DocumentVersionResponse[]>(`/api/documents/${id}/versions`);
  }

  create(
    title: string,
    category: string,
    studyId: number | null,
    subjectId: number | null,
    file: File,
  ): Observable<DocumentResponse> {
    const form = new FormData();
    form.append('title', title);
    form.append('category', category);
    if (studyId != null) {
      form.append('studyId', String(studyId));
    }
    if (subjectId != null) {
      form.append('subjectId', String(subjectId));
    }
    form.append('file', file);
    return this.http.post<DocumentResponse>('/api/documents', form);
  }

  addVersion(id: number, file: File): Observable<DocumentVersionResponse> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<DocumentVersionResponse>(`/api/documents/${id}/versions`, form);
  }

  download(id: number, versionNumber: number): Observable<Blob> {
    return this.http.get(`/api/documents/${id}/versions/${versionNumber}/download`, { responseType: 'blob' });
  }

  submitForReview(id: number, versionNumber: number): Observable<DocumentReviewResponse> {
    return this.http.post<DocumentReviewResponse>(`/api/documents/${id}/versions/${versionNumber}/submit`, {});
  }

  review(id: number, versionNumber: number, action: string, comment: string | null): Observable<DocumentReviewResponse> {
    return this.http.post<DocumentReviewResponse>(`/api/documents/${id}/versions/${versionNumber}/review`, {
      action,
      comment,
    });
  }

  approve(
    id: number,
    versionNumber: number,
    action: string,
    comment: string | null,
    password: string | null,
    reason: string | null,
  ): Observable<DocumentReviewResponse> {
    return this.http.post<DocumentReviewResponse>(`/api/documents/${id}/versions/${versionNumber}/approve`, {
      action,
      comment,
      password,
      reason,
    });
  }

  reviewHistory(id: number, versionNumber: number): Observable<DocumentReviewResponse[]> {
    return this.http.get<DocumentReviewResponse[]>(`/api/documents/${id}/versions/${versionNumber}/reviews`);
  }

  approvalQueue(stage: 'REVIEW' | 'APPROVAL', page = 0, size = 20): Observable<Page<DocumentVersionResponse>> {
    return this.http.get<Page<DocumentVersionResponse>>('/api/documents/approval-queue', {
      params: { stage, page, size },
    });
  }
}
