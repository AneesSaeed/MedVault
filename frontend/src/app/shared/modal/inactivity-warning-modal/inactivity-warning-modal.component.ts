import { CommonModule } from '@angular/common';
import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { InactivityTimeoutService } from '../../../core/services/inactivity-timeout.service';

@Component({
  selector: 'app-inactivity-warning-modal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './inactivity-warning-modal.component.html',
  styleUrls: ['./inactivity-warning-modal.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class InactivityWarningModalComponent {
  private readonly inactivity = inject(InactivityTimeoutService);
  readonly warning$ = this.inactivity.warning$;

  stay(ev?: Event): void {
    ev?.stopPropagation();
    ev?.preventDefault();
    this.inactivity.stayConnected();
  }

  logout(ev?: Event): void {
    ev?.stopPropagation();
    ev?.preventDefault();
    this.inactivity.logoutNow();
  }
}
