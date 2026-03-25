import { Toaster } from "@/components/ui/sonner";
import NotificationProvider from "@/providers/notification-provider";

export default function ShopLayout({
    children,
}: {
    children: React.ReactNode
}) {
    return (
        <div className="min-h-screen flex flex-col">
            {/* GNB (Global Navigation Bar) 등 공통 헤더 위치 */}

            {/* NotificationProvider로 children을 감싸 백그라운드 알림을 활성화합니다. */}
            <NotificationProvider>
                <main className="flex-grow">
                    {children}
                </main>
            </NotificationProvider>

            {/* 공통 푸터 위치 */}
            <Toaster position="top-center" />
        </div>
    )
}