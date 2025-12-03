import { Component, OnInit } from '@angular/core';
import { UserService } from '../core/services/user.service';
import { UserContextService } from '../core/services/user-context.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss']
})
export class HomeComponent implements OnInit {

  constructor(
    private userService: UserService,
    private userContext: UserContextService,
    private router: Router
  ) {}

  ngOnInit(): void {

    this.userService.userExists().subscribe(exists => {
      // If not onboarded: go to onboarding
      if (!exists) {
        this.router.navigate(['/onboarding']);
        return;
      }

      // If onboarded: load role, userId, etc.
      this.userContext.loadUserContext();
    });
  }
}
