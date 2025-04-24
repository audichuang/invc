import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { TaskFormComponent } from './components/task-form/task-form.component';

const routes: Routes = [
    { path: '', component: TaskFormComponent },
    { path: '**', redirectTo: '' }
];

@NgModule({
    imports: [RouterModule.forRoot(routes)],
    exports: [RouterModule]
})
export class AppRoutingModule { } 