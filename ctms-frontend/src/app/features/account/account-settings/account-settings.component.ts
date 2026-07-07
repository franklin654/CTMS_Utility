import { Component, signal } from '@angular/core';
import { FormControl, FormGroup, FormGroupDirective, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

const USERNAME_PATTERN = /^[a-zA-Z0-9._-]+$/;

/** Self-service login-credential changes (username/email/password), available to every role.
 * Distinct from the Patient Portal's "My Profile" page, which edits Subject.contactEmail (a
 * clinical contact field) rather than User.email (the login credential edited here). */
@Component({
  selector: 'app-account-settings',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './account-settings.component.html',
})
export class AccountSettingsComponent {
  readonly usernameForm = new FormGroup({
    currentPassword: new FormControl('', { nonNullable: true, validators: Validators.required }),
    newUsername: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(3), Validators.maxLength(100), Validators.pattern(USERNAME_PATTERN)],
    }),
  });

  readonly emailForm = new FormGroup({
    currentPassword: new FormControl('', { nonNullable: true, validators: Validators.required }),
    newEmail: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email, Validators.maxLength(255)],
    }),
  });

  readonly passwordForm = new FormGroup({
    currentPassword: new FormControl('', { nonNullable: true, validators: Validators.required }),
    newPassword: new FormControl('', { nonNullable: true, validators: Validators.required }),
  });

  readonly usernameError = signal<string | null>(null);
  readonly emailError = signal<string | null>(null);
  readonly emailSuccess = signal<string | null>(null);
  readonly passwordError = signal<string | null>(null);

  constructor(
    private readonly authService: AuthService,
    private readonly router: Router,
  ) {}

  submitUsername(): void {
    if (this.usernameForm.invalid) {
      return;
    }
    this.usernameError.set(null);
    const { currentPassword, newUsername } = this.usernameForm.getRawValue();
    this.authService.changeUsername(currentPassword, newUsername).subscribe({
      next: () => {
        // Username is the JWT subject -- all sessions were just invalidated server-side, so
        // proactively clear local tokens and force a fresh login rather than waiting for a 401.
        this.authService.logout().subscribe({
          complete: () => this.router.navigate(['/login']),
          error: () => this.router.navigate(['/login']),
        });
      },
      error: (err) => {
        this.usernameError.set(
          err.error?.violations?.join('; ') ?? err.error?.message ?? 'Could not change username.',
        );
      },
    });
  }

  submitEmail(formDirective: FormGroupDirective): void {
    if (this.emailForm.invalid) {
      return;
    }
    this.emailError.set(null);
    this.emailSuccess.set(null);
    const { currentPassword, newEmail } = this.emailForm.getRawValue();
    this.authService.changeEmail(currentPassword, newEmail).subscribe({
      next: () => {
        this.emailSuccess.set('Email updated.');
        // formDirective.resetForm() (not emailForm.reset()) also clears the directive's
        // "submitted" flag -- otherwise Material keeps showing the now-empty required fields
        // as invalid/red after a successful submit, since ErrorStateMatcher checks
        // `invalid && (touched || submitted)` and reset() alone doesn't clear `submitted`.
        formDirective.resetForm({ currentPassword: '', newEmail: '' });
      },
      error: (err) => {
        this.emailError.set(err.error?.violations?.join('; ') ?? err.error?.message ?? 'Could not change email.');
      },
    });
  }

  submitPassword(): void {
    if (this.passwordForm.invalid) {
      return;
    }
    this.passwordError.set(null);
    const { currentPassword, newPassword } = this.passwordForm.getRawValue();
    this.authService.changePassword(currentPassword, newPassword).subscribe({
      next: () => this.router.navigate(['/login']),
      error: (err) => {
        this.passwordError.set(
          err.error?.violations?.join('; ') ?? err.error?.message ?? 'Could not change password.',
        );
      },
    });
  }
}
