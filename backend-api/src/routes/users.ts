import { Router } from 'express';

const router = Router();

router.get('/profile', (req, res) => {
  res.json({ message: 'User profile endpoint - Coming soon' });
});

export default router; 