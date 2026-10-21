import { Routes } from '@angular/router';
import { workspaceGuard } from './core/workspace.guard';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Log in · Fine',
    loadComponent: () => import('./pages/login/login.page').then((m) => m.LoginPage),
  },
  {
    path: '',
    pathMatch: 'full',
    title: 'Fine',
    canActivate: [workspaceGuard],
    loadComponent: () => import('./pages/workspace/workspace.page').then((m) => m.WorkspacePage),
  },
  { path: '**', redirectTo: '' },
];
