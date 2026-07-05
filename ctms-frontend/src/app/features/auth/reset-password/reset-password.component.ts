import { Component, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, RouterLink],
  templateUrl: './reset-password.component.html',
})
export class ResetPasswordComponent {
  readonly form: FormGroup<{ token: FormControl<string>; newPassword: FormControl<string> }>;
  readonly errorMessage = signal<string | null>(null);
  readonly success = signal(false);

  constructor(
    private readonly authService: AuthService,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
  ) {
    this.form = new FormGroup({
      token: new FormControl(this.route.snapshot.queryParamMap.get('token') ?? '', {
        nonNullable: true,
        validators: Validators.required,
      }),
      newPassword: new FormControl('', { nonNullable: true, validators: Validators.required }),
    });
  }

  submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.errorMessage.set(null);
    const { token, newPassword } = this.form.getRawValue();
    this.authService.resetPassword(token, newPassword).subscribe({
      next: () => {
        this.success.set(true);
        setTimeout(() => this.router.navigate(['/login']), 2000);
      },
      error: (err) => {
        this.errorMessage.set(
          err.error?.violations?.join('; ') ?? 'That reset link is invalid or has expired.',
        );
      },
    });
  }
}
