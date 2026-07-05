import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { HasRoleDirective } from '../../core/auth/has-role.directive';
import { NotificationBellComponent } from '../../shared/notification-bell/notification-bell.component';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, MatButtonModule, NotificationBellComponent, HasRoleDirective],
  templateUrl: './shell.component.html',
})
export class ShellComponent {
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
