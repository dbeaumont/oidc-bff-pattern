import { Component, OnInit, inject, signal } from '@angular/core';
import { AuthService, UserInfo } from '../../core/services/auth.service';
import { ItemService, Item } from '../../core/services/item.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit {
  private readonly itemService = inject(ItemService);
  readonly authService = inject(AuthService);

  readonly user = signal<UserInfo | null>(null);
  readonly items = signal<Item[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.authService.getUserInfo().subscribe(user => this.user.set(user));
    this.loadItems();
  }

  loadItems(): void {
    this.loading.set(true);
    this.error.set(null);
    this.itemService.getAll().subscribe({
      next: items => {
        this.items.set(items);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les données.');
        this.loading.set(false);
      }
    });
  }

  logout(): void {
    this.authService.logout();
  }
}
