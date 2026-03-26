import Link from 'next/link'
import Image from 'next/image'
import { useTheme } from 'next-themes'
import { usePathname } from 'next/navigation'
import SidebarContent from './Sidebaritems'
import SimpleBar from 'simplebar-react'
import { Icon } from '@iconify/react'
import {
  AMLogo,
  AMMenu,
  AMMenuItem,
  AMSidebar,
  AMSubmenu,
} from 'tailwind-sidebar'
import 'tailwind-sidebar/styles.css'

// 3. Pro 배지 렌더링 로직 완전 제거 (깔끔한 메뉴 렌더링 함수)
const renderSidebarItems = (
  items: any[],
  currentPath: string,
  onClose?: () => void,
  isSubItem: boolean = false
) => {
  return items.map((item, index) => {
    const isSelected = currentPath === item?.url
    const IconComp = item.icon || null

    const iconElement = IconComp ? (
      <Icon icon={IconComp} height={21} width={21} />
    ) : (
      <Icon icon={'ri:checkbox-blank-circle-line'} height={9} width={9} />
    )

    // Heading (예: "쇼핑몰 관리", "주문/배송 관리")
    if (item.heading) {
      return (
        <div className='mb-1 mt-4 first:mt-0' key={item.heading}>
          <AMMenu
            subHeading={item.heading}
            ClassName='hide-menu leading-21 text-slate-500 font-bold uppercase text-xs tracking-wider'
          />
        </div>
      )
    }

    // Submenu (하위 메뉴가 있을 경우)
    if (item.children?.length) {
      return (
        <AMSubmenu
          key={item.id}
          icon={iconElement}
          title={item.name}
          ClassName='mt-0.5 text-slate-700 dark:text-slate-300'>
          {renderSidebarItems(item.children, currentPath, onClose, true)}
        </AMSubmenu>
      )
    }

    // Regular menu item (일반 메뉴 버튼)
    const linkTarget = item.url?.startsWith('https') ? '_blank' : '_self'

    const itemClassNames = isSubItem
      ? `mt-0.5 text-slate-600 dark:text-slate-400 !hover:bg-transparent ${isSelected ? '!bg-transparent !text-primary font-semibold' : ''
      } !px-1.5`
      : `mt-0.5 text-slate-700 dark:text-slate-300`

    return (
      <div onClick={onClose} key={index}>
        <AMMenuItem
          key={item.id}
          icon={iconElement}
          isSelected={isSelected}
          link={item.url || undefined}
          target={linkTarget}
          disabled={item.disabled}
          component={Link}
          className={`${itemClassNames}`}>
          <span className='truncate flex-1'>{item.title || item.name}</span>
        </AMMenuItem>
      </div>
    )
  })
}

const SidebarLayout = ({ onClose }: { onClose?: () => void }) => {
  const pathname = usePathname()
  const { theme } = useTheme()

  const sidebarMode = theme === 'light' || theme === 'dark' ? theme : undefined

  return (
    <AMSidebar
      collapsible='none'
      animation={true}
      showProfile={false}
      width={'270px'}
      showTrigger={false}
      mode={sidebarMode}
      className='fixed left-0 top-0 xl:top-[68px] border-r border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 z-10 h-screen'>

      {/* 1. 로고 이미지 적용 (FullLogo 제거) */}
      <div className='px-6 h-[68px] flex items-center xl:hidden border-b border-slate-100 dark:border-slate-800'>
        <Link href="/admin/dashboard" className="flex items-center gap-2">
          <Image
            src='/admin/images/logos/logo-square.png'
            alt='Admin Logo'
            width={32}
            height={32}
            className='object-contain'
          />
          <span className="font-bold text-lg tracking-tight text-slate-800 dark:text-white">
            ToyShop Admin
          </span>
        </Link>
      </div>

      {/* Sidebar Menu Scroll Area */}
      <SimpleBar className='h-[calc(100vh-68px)]'>
        <div className='px-4 py-6'>
          {SidebarContent.map((section, index) => (
            <div key={index}>
              {renderSidebarItems(
                [
                  ...(section.heading ? [{ heading: section.heading }] : []),
                  ...(section.children || []),
                ],
                pathname,
                onClose
              )}
            </div>
          ))}

          {/* 2. 실용적인 시스템 상태 위젯으로 교체 (홍보용 로켓 제거) */}
          <div className='mt-8 pt-6 border-t border-slate-100 dark:border-slate-800'>
            <div className='flex flex-col gap-3 p-4 bg-slate-50 dark:bg-slate-800/50 rounded-xl border border-slate-200 dark:border-slate-700'>

              <div className="flex items-center justify-between">
                <span className='text-xs font-semibold text-slate-500 uppercase tracking-wider'>
                  System Status
                </span>
                {/* 깜빡이는 녹색 불빛 효과 */}
                <span className="relative flex h-2 w-2">
                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                  <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
                </span>
              </div>

              <div className="flex items-center gap-2">
                <Icon icon="solar:server-minimalistic-line-duotone" className="text-emerald-500" width={20} />
                <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
                  정상 작동 중
                </span>
              </div>

              <div className="text-[10px] text-slate-400">
                마지막 업데이트: 방금 전
              </div>
            </div>
          </div>

        </div>
      </SimpleBar>
    </AMSidebar>
  )
}

export default SidebarLayout