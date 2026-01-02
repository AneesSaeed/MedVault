import { Component, OnInit, inject } from '@angular/core';
import { AuthService } from './core/services/auth.service';
import { InactivityTimeoutService } from './core/services/inactivity-timeout.service';
import { InactivityWarningModalComponent } from './shared/modal/inactivity-warning-modal/inactivity-warning-modal.component';
import { HeaderComponent } from './header/header.component';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss'],
  standalone: true,
  imports: [HeaderComponent, RouterModule, InactivityWarningModalComponent]
})
export class AppComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly inactivityTimeout = inject(InactivityTimeoutService);

  async ngOnInit(): Promise<void> {
    if (this.auth.isLoggedIn()) {
      this.inactivityTimeout.startWatching();
    }
  }
}
