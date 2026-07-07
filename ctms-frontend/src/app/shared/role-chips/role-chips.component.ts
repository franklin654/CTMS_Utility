import { Component, Input } from '@angular/core';

/** Renders a list of role codes as the app's shared `.chip-info` pills -- used anywhere role
 * codes are displayed (read-only lists, mat-select triggers) so all three spots stay visually
 * identical instead of copy-pasted markup drifting apart. */
@Component({
  selector: 'app-role-chips',
  standalone: true,
  template: `
    <span class="flex flex-wrap gap-1">
      @for (role of roles; track role) {
        <span class="chip chip-info">{{ role }}</span>
      }
    </span>
  `,
})
export class RoleChipsComponent {
  @Input() roles: string[] = [];
}
