import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import DefaultReceiptButton, { ReceiptButton } from '../ReceiptButton';
import * as receipts from '../../../../api/receipts';

vi.mock('../../../../api/receipts', () => ({
  downloadReceipt: vi.fn(),
  receiptFileName: (tn) => `SwiftDrop-Receipt-${tn}.pdf`,
}));

describe('TST-040 ReceiptButton (Member #6, FR-UI-011)', () => {
  let clicked;
  let openSpy;

  beforeEach(() => {
    vi.clearAllMocks();
    clicked = [];
    URL.createObjectURL = vi.fn(() => 'blob:receipt-1');
    URL.revokeObjectURL = vi.fn();
    openSpy = vi.spyOn(window, 'open').mockImplementation(() => null);
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function click() {
      clicked.push({ href: this.href, download: this.download });
    });
  });

  afterEach(() => vi.restoreAllMocks());

  it('is exported as named and default export', () => {
    expect(DefaultReceiptButton).toBe(ReceiptButton);
  });

  it('downloads through the blob flow with the right file name and revokes the URL', async () => {
    receipts.downloadReceipt.mockResolvedValue(new Blob(['%PDF-1.4'], { type: 'application/pdf' }));
    render(<ReceiptButton parcelId="p-1" trackingNumber="SD-7K3M9QX2TA" />);

    fireEvent.click(screen.getByRole('button', { name: /download receipt/i }));

    await waitFor(() => expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:receipt-1'));
    expect(receipts.downloadReceipt).toHaveBeenCalledWith('p-1');
    expect(clicked).toEqual([{ href: 'blob:receipt-1', download: 'SwiftDrop-Receipt-SD-7K3M9QX2TA.pdf' }]);
    expect(openSpy).not.toHaveBeenCalled();
    // Never a plain link to the API in the page.
    expect(document.querySelector('a[href*="/api/"]')).toBeNull();
  });

  it('shows a loading state while the PDF is prepared', async () => {
    let resolve;
    receipts.downloadReceipt.mockReturnValue(new Promise((r) => { resolve = r; }));
    render(<ReceiptButton parcelId="p-1" trackingNumber="SD-1" />);

    fireEvent.click(screen.getByRole('button', { name: /download receipt/i }));

    const busy = await screen.findByRole('button', { name: /preparing receipt/i });
    expect(busy).toBeDisabled();
    resolve(new Blob(['x']));
    await screen.findByRole('button', { name: /download receipt/i });
  });

  it('shows an error and stays usable when the download fails', async () => {
    receipts.downloadReceipt.mockRejectedValue({ status: 503, code: 'SERVICE_UNAVAILABLE' });
    render(<ReceiptButton parcelId="p-1" trackingNumber="SD-1" />);

    fireEvent.click(screen.getByRole('button', { name: /download receipt/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/could not be downloaded/i);
    expect(screen.getByRole('button', { name: /download receipt/i })).toBeEnabled();
    expect(URL.createObjectURL).not.toHaveBeenCalled();
  });

  it('explains a missing receipt (404)', async () => {
    receipts.downloadReceipt.mockRejectedValue({ status: 404, code: 'NOT_FOUND' });
    render(<ReceiptButton parcelId="p-1" trackingNumber="SD-1" />);

    fireEvent.click(screen.getByRole('button', { name: /download receipt/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/could not be found/i);
  });
});
