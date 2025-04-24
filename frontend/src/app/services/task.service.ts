import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, Subject } from 'rxjs';
import { v4 as uuidv4 } from 'uuid';

export interface TaskRequest {
    correlationId: string;
    taskName: string;
    numberOfSubtasks: number;
}

export interface TaskEvent {
    correlationId: string;
    status: string;
    message: string;
    result?: any;
    finalEvent: boolean;
    receivedAt?: Date;
}

@Injectable({
    providedIn: 'root'
})
export class TaskService {
    private apiUrl = 'http://localhost:8080/api';
    private eventSubject = new Subject<TaskEvent>();
    public events$ = this.eventSubject.asObservable();
    private eventSource: EventSource | null = null;

    constructor(private http: HttpClient) { }

    /**
     * 產生相關ID
     */
    generateCorrelationId(): string {
        return uuidv4();
    }

    /**
     * 發起任務請求
     */
    initiateTask(request: TaskRequest): Observable<string> {
        return this.http.post<string>(`${this.apiUrl}/first-api`, request, {
            responseType: 'text' as 'json'
        });
    }

    /**
     * 建立 SSE 連接
     */
    connectToEventStream(correlationId: string): void {
        // 關閉現有連接（如果有）
        this.disconnectEventStream();

        // 建立新的 SSE 連接
        this.eventSource = new EventSource(`${this.apiUrl}/events/${correlationId}`);

        // Helper function to process and add timestamp
        const processEvent = (eventData: string, eventName?: string) => {
            try {
                const taskEvent: TaskEvent = JSON.parse(eventData);
                taskEvent.receivedAt = new Date();
                console.log(`收到 SSE 事件 (${eventName || 'message'}):`, taskEvent);
                this.eventSubject.next(taskEvent);

                if (taskEvent.finalEvent) {
                    this.disconnectEventStream();
                }
            } catch (error) {
                console.error(`解析 SSE 事件 (${eventName || 'message'}) 失敗:`, eventData, error);
                this.eventSubject.next({
                    correlationId,
                    status: 'ERROR',
                    message: `客戶端解析事件失敗: ${eventData}`,
                    finalEvent: true,
                    receivedAt: new Date()
                });
                this.disconnectEventStream();
            }
        };

        this.eventSource.onmessage = (event) => {
            processEvent(event.data);
        };

        // Use helper for specific listeners
        this.eventSource.addEventListener('CONNECTED', (event: any) => processEvent(event.data, 'CONNECTED'));
        this.eventSource.addEventListener('PROCESSING', (event: any) => processEvent(event.data, 'PROCESSING'));
        this.eventSource.addEventListener('SUBTASK_COMPLETED', (event: any) => processEvent(event.data, 'SUBTASK_COMPLETED'));
        this.eventSource.addEventListener('COMPLETED', (event: any) => processEvent(event.data, 'COMPLETED'));
        this.eventSource.addEventListener('FAILED', (event: any) => processEvent(event.data, 'FAILED'));

        this.eventSource.onerror = (error) => {
            console.error('SSE 連接錯誤:', error);
            this.disconnectEventStream();
            this.eventSubject.next({
                correlationId,
                status: 'ERROR',
                message: 'SSE 連接出錯',
                finalEvent: true,
                receivedAt: new Date()
            });
        };
    }

    /**
     * 關閉 SSE 連接
     */
    disconnectEventStream(): void {
        if (this.eventSource) {
            this.eventSource.close();
            this.eventSource = null;
            console.log('SSE 連接已關閉');
        }
    }
} 