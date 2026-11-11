import { expect, test } from '@playwright/test'
import { installSession, mockApi } from './fixtures'

test('subscription entry points stay hidden when the feature is disabled', async ({ page }) => {
  await installSession(page, 'admin')
  const api = await mockApi(page, 'admin')

  await page.goto('/dashboard')
  await page.locator('#dash-avatar').click()

  await expect(page.getByRole('button', { name: 'Upgrade Plan' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Account Settings' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Sign Out' })).toBeVisible()

  await page.goto('/upgrade-plan')
  await expect(page).toHaveURL(/\/settings(?:#account)?$/)
  await expect(page.getByText('Subscription & Billing')).toHaveCount(0)
  expect(api.count('GET', '/api/v1/subscription')).toBe(0)
})
