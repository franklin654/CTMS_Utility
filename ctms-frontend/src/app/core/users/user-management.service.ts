import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from '../studies/study.service';

export interface UserResponse {
  id: number;
  username: string;
  email: string;
  fullName: string;
  enabled: boolean;
  accountLocked: boolean;
  roles: string[];
  createdAt: string;
}

export interface CreateUserRequest {
  username: string;
  email: string;
  fullName: string;
  roles: string[];
}

export interface CreateUserResponse {
  user: UserResponse;
  temporaryPassword: string;
}

@Injectable({ providedIn: 'root' })
export class UserManagementService {
  constructor(private readonly http: HttpClient) {}

  list(page = 0, size = 20): Observable<Page<UserResponse>> {
    return this.http.get<Page<UserResponse>>('/api/admin/users', { params: { page, size } });
  }

  listRoleCodes(): Observable<string[]> {
    return this.http.get<string[]>('/api/admin/users/roles');
  }

  create(req: CreateUserRequest): Observable<CreateUserResponse> {
    return this.http.post<CreateUserResponse>('/api/admin/users', req);
  }

  updateRoles(id: number, roles: string[]): Observable<UserResponse> {
    return this.http.put<UserResponse>(`/api/admin/users/${id}/roles`, { roles });
  }

  setEnabled(id: number, enabled: boolean): Observable<UserResponse> {
    return this.http.put<UserResponse>(`/api/admin/users/${id}/status`, { enabled });
  }
}
