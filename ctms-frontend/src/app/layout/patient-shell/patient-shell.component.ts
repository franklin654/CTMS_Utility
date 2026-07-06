import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { NotificationBellComponent } from '../../shared/notification-bell/notification-bell.component';

/** Patient-facing shell, separate from the staff ShellComponent -- copy says "Patient"/"My ___"
 * throughout per CLAUDE.md's terminology split (Subject in code/DB, Patient in portal-facing UI). */
@Component({
  selector: 'app-patient-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatButtonModule, NotificationBellComponent],
  templateUrl: './patient-shell.component.html',
})
export class PatientShellComponent {
  constructor(
    readonly authService: AuthService,
    private readonly router: Router,
  ) {}

  logout(): void {
    this.authService.logout().subscribe({
      complete: () => this.router.navigate(['/login']),
      error: () => this.router.navigate(['/login']),
    });
  }
}
