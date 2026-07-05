import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface TestResultResponse {
  id: number;
  subjectId: number;
  subjectCode: string;
  visitId: number;
  visitName: string;
  testName: string;
  resultValue: string;
  units: string | null;
  referenceRange: string | null;
  abnormal: boolean;
  status: 'RECORDED' | 'REVIEWED';
  notes: string | null;
  reviewedByUsername: string | null;
  reviewedAt: string | null;
  createdByUsername: string;
  createdAt: string;
}

export interface CreateTestResultRequest {
  subjectId: number;
  visitId: number;
  testName: string;
  resultValue: string;
  units: string | null;
  referenceRange: string | null;
  abnormal: boolean;
  notes: string | null;
}

export interface TestResultAttachmentResponse {
  id: number;
  testResultId: number;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  uploadedByUsername: string;
  uploadedAt: string;
}

@Injectable({ providedIn: 'root' })
export class TestResultService {
  constructor(private readonly http: HttpClient) {}

  list(subjectId: number): Observable<TestResultResponse[]> {
    return this.http.get<TestResultResponse[]>('/api/test-results', { params: { subjectId } });
  }

  record(req: CreateTestResultRequest): Observable<TestResultResponse> {
    return this.http.post<TestResultResponse>('/api/test-results', req);
  }

  review(id: number): Observable<TestResultResponse> {
    return this.http.post<TestResultResponse>(`/api/test-results/${id}/review`, {});
  }

  attachments(testResultId: number): Observable<TestResultAttachmentResponse[]> {
    return this.http.get<TestResultAttachmentResponse[]>(`/api/test-results/${testResultId}/attachments`);
  }

  upload(testResultId: number, file: File): Observable<TestResultAttachmentResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<TestResultAttachmentResponse>(`/api/test-results/${testResultId}/attachments`, formData);
  }

  download(attachmentId: number): Observable<Blob> {
    return this.http.get(`/api/attachments/${attachmentId}/download`, { responseType: 'blob' });
  }
}
