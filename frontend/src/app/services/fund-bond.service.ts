import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, Subject } from 'rxjs';
import { TaskEvent } from './task.service';

export interface FundBondTaskRequest {
    correlationId: string;
    taskName: string;
    numberOfSubtasks: number;
    items: string[];
}

@Injectable({
    providedIn: 'root'
})
export class FundBondService {
    private fundApiUrl = 'http://localhost:8080/api'; // 基金系統API
    private bondApiUrl = 'http://localhost:8081/api'; // 債券系統API - 使用新的反向代理
    private eventSubject = new Subject<TaskEvent>();
    public events$ = this.eventSubject.asObservable();

    constructor(private http: HttpClient) { }

    /**
     * 發起基金任務請求
     */
    initiateFundTask(request: FundBondTaskRequest): Observable<string> {
        return this.http.post<string>(`${this.fundApiUrl}/first-api`, {
            correlationId: request.correlationId,
            taskName: request.taskName,
            numberOfSubtasks: request.numberOfSubtasks
        }, {
            responseType: 'text' as 'json'
        });
    }

    /**
     * 發起債券任務請求
     */
    initiateBondTask(request: FundBondTaskRequest): Observable<string> {
        return this.http.post<string>(`${this.bondApiUrl}/bond-api`, {
            correlationId: request.correlationId,
            taskName: request.taskName,
            numberOfSubtasks: request.numberOfSubtasks
        }, {
            responseType: 'text' as 'json'
        });
    }

    /**
     * 通知錯誤
     */
    notifyError(correlationId: string, message: string): void {
        this.eventSubject.next({
            correlationId,
            status: 'ERROR',
            message,
            finalEvent: true,
            receivedAt: new Date()
        });
    }
} 