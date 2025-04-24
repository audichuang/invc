import { Routes } from '@angular/router';
import { TaskFormComponent } from './components/task-form/task-form.component';

export const routes: Routes = [
    { path: '', component: TaskFormComponent },
    { path: '**', redirectTo: '' }
]; 