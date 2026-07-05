import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface UserSummaryResponse {
  id: number;
  username: string;
  fullName: string;
}

@Injectable({ providedIn: 'root' })
export class UserService {
  constructor(private readonly http: HttpClient) {}

  searchByRole(role: string, search: string): Observable<UserSummaryResponse[]> {
    return this.http.get<UserSummaryResponse[]>('/api/users', { params: { role, search } });
  }
}
