import { Directive, Input, TemplateRef, ViewContainerRef, effect } from '@angular/core';
import { AuthService } from './auth.service';

/** Structural directive: *appHasRole="['ADMIN', 'STUDY_MANAGER']" shows content only if the
 * current user holds at least one of the given roles. Purely a UX nicety -- the server-side
 * @PreAuthorize checks are the actual enforcement. */
@Directive({
  selector: '[appHasRole]',
  standalone: true,
})
export class HasRoleDirective {
  private roles: string[] = [];
  private hasView = false;

  constructor(
    private readonly templateRef: TemplateRef<unknown>,
    private readonly viewContainer: ViewContainerRef,
    private readonly authService: AuthService,
  ) {
    effect(() => {
      this.authService.currentUser();
      this.updateView();
    });
  }

  @Input() set appHasRole(roles: string[]) {
    this.roles = roles;
    this.updateView();
  }

  private updateView(): void {
    const shouldShow = this.roles.length === 0 || this.authService.hasAnyRole(this.roles);
    if (shouldShow && !this.hasView) {
      this.viewContainer.createEmbeddedView(this.templateRef);
      this.hasView = true;
    } else if (!shouldShow && this.hasView) {
      this.viewContainer.clear();
      this.hasView = false;
    }
  }
}
