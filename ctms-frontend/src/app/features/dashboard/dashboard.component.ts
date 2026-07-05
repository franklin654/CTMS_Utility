import { Component } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  template: `
    <div class="bg-white rounded-lg shadow p-6">
      <h1 class="text-lg font-semibold text-gray-900">Welcome, {{ authService.currentUser()?.username }}</h1>
      <p class="text-sm text-gray-600 mt-2">
        This is a placeholder landing page. Study/site/subject dashboards land in later phases.
      </p>
    </div>
  `,
})
export class DashboardComponent {
  constructor(readonly authService: AuthService) {}
}
