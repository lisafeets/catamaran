import { Router } from 'express';

const router = Router();

router.get('/connections', (req, res) => {
  res.json({ message: 'Family connections endpoint - Coming soon' });
});

export default router; 