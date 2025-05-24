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
    private fundApiUrl = 'http://localhost:8000/api'; // 基金系統API - 使用主反向代理
    private bondApiUrl = 'http://localhost:8000/api'; // 債券系統API - 使用主反向代理
    private eventSubject = new Subject<TaskEvent>();
    public events$ = this.eventSubject.asObservable();

    constructor(private http: HttpClient) { }

    /**
     * 發起基金任務請求
     */
    initiateFundTask(request: FundBondTaskRequest): Observable<string> {
        const headers = {
            'Content-Type': 'application/json',
            'X-Cluster-Route': 'cluster1'
        };

        return this.http.post<string>(`${this.fundApiUrl}/fund-api`, {
            correlationId: request.correlationId,
            taskName: request.taskName,
            numberOfSubtasks: request.numberOfSubtasks,
            // items: request.items
        }, {
            responseType: 'text' as 'json',
            headers
        });
    }

    /**
     * 發起債券任務請求 (暫時使用fund-api，債券系統未實現)
     */
    initiateBondTask(request: FundBondTaskRequest): Observable<string> {
        const headers = {
            'Content-Type': 'application/json',
            'X-Cluster-Route': 'cluster2'
        };

        return this.http.post<string>(`${this.bondApiUrl}/fund-api`, {
            correlationId: request.correlationId,
            taskName: request.taskName,
            numberOfSubtasks: request.numberOfSubtasks,
            // items: request.items
        }, {
            responseType: 'text' as 'json',
            headers
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